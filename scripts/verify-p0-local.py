"""Isolated localhost acceptance fixture. Never sends real payments or edits database rows.
Requires docker/compose/overlays/compose.p0.local.yaml (DEMO payments) plus docker/compose/overlays/compose.email.local.yaml (local SMTP inbox).
Secrets are saved only in git-ignored .env.p0-session.json; output is sanitized.
"""
import json, uuid, secrets, time, urllib.request, urllib.error, re
from datetime import datetime, timedelta
from pathlib import Path
BASE = "http://localhost:8088/api/v1"
results = []
def api(method, path, body=None, token=None, expected=200):
    headers={"Content-Type":"application/json"}
    if token: headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=20) as response: code=response.status; data=json.load(response)
    except urllib.error.HTTPError as error:
        code=error.code
        try: data=json.load(error)
        except Exception: data={}
    if code != expected: raise RuntimeError(f"{method} {path}: HTTP {code}; expected {expected}; {data.get('message','API error')}")
    return data.get("data")
def check(name,condition):
    if not condition: raise RuntimeError(name)
    results.append({"check":name,"passed":True})
def local_email_code(email):
    for attempt in range(30):
        with urllib.request.urlopen("http://127.0.0.1:8025/api/v1/messages?limit=100",timeout=5) as response:
            messages=json.load(response).get("messages",[])
        for message in messages:
            if any(to.get("Address", "").lower()==email.lower() for to in message.get("To",[])):
                with urllib.request.urlopen("http://127.0.0.1:8025/api/v1/message/"+message["ID"],timeout=5) as response:
                    match=re.search(r"\b[0-9]{6}\b",json.load(response).get("Text", ""))
                if match: return match.group()
        time.sleep(.2)
    raise RuntimeError("OTP email not received in the local SMTP inbox")

def account(role):
    phone="09"+str(secrets.randbelow(100000000)).zfill(8)
    password=secrets.token_urlsafe(18)
    email=f"p0-{uuid.uuid4().hex[:10]}@example.test"
    api("POST","/auth/otp/send",{"email":email})
    code=local_email_code(email)
    body={"phone":phone,"password":password,"otpCode":code,"email":email,"role":role,"fullName":"P0 local "+role}
    if role=="MERCHANT": body.update(bankName="Local Test Bank",bankAccountNum="123456789",citizenId=str(secrets.randbelow(900000000000)+100000000000))
    api("POST","/auth/register",body,expected=201)
    auth=api("POST","/auth/login",{"phone":phone,"password":password})
    check("OTP registration and login "+role,auth["role"]==role)
    return {"phone":phone,"password":password,"token":auth["token"]}
def main():
    driver=account("DRIVER"); merchant=account("MERCHANT")
    admin=api("POST","/auth/login",{"phone":"0903829145","password":"password123"})["token"]
    d=driver["token"];m=merchant["token"]
    v=api("POST","/vehicles",{"licensePlate":"51C-"+str(secrets.randbelow(90000)+10000),"vehicleType":"HEAVY_TRUCK","weightTon":12,"brandModel":"P0 local acceptance"},d,201)
    lot=api("POST","/merchant/parking-lots",{"name":"P0 local acceptance "+uuid.uuid4().hex[:8],"address":"Local acceptance fixture, TP HCM","latitude":10.8,"longitude":106.7},m,201)
    lid=lot["id"]
    Path(".env.p0-session.json").write_text(json.dumps({"driver":driver,"merchant":merchant,"lotId":lid,"vehicleId":v["id"]}))
    api("PUT",f"/merchant/parking-lots/{lid}/slots",{"vehicleType":"HEAVY_TRUCK","totalCapacity":2},m)
    api("PUT",f"/merchant/parking-lots/{lid}/pricing",{"vehicleType":"HEAVY_TRUCK","basePricePerHour":25000,"surgeMultiplier":1.2,"surgeStartTime":"17:00:00","surgeEndTime":"20:00:00","overtimePenaltyMultiplier":2},m)
    api("PUT",f"/admin/parking-lots/{lid}/approve",{},admin)
    lots=api("GET","/parking-lots/search?vehicleType=HEAVY_TRUCK&latitude=10.8&longitude=106.7&radiusKm=20&size=100",token=d)
    check("Approved configured lot is returned by map search",any(row["id"]==lid for row in lots["content"]))
    def create(minutes):
        start=(datetime.now()+timedelta(minutes=minutes)).replace(microsecond=0)
        payload={"parkingLotId":lid,"vehicleId":v["id"],"vehicleType":"HEAVY_TRUCK","expectedCheckInTime":start.isoformat(),"expectedCheckOutTime":(start+timedelta(hours=2)).isoformat(),"requestId":str(uuid.uuid4()),"advanceDeposit":1,"totalAmount":1,"merchantId":999999}
        b=api("POST","/bookings",payload,d,201)
        check("Server ignores client price and snapshots overtime x2",float(b["totalAmount"])>=50000 and float(b["advanceDeposit"])==round(float(b["totalAmount"])*.2) and float(b["overtimePenaltyMultiplier"])==2)
        replay=api("POST","/bookings",payload,d,201)
        check("Booking retry returns same ID",replay["id"]==b["id"])
        return b
    early=create(40)
    paid=api("POST","/payments/demo-confirm",{"bookingId":early["id"],"merchantId":999999,"amount":1},d)
    check("Explicit local DEMO uses authoritative 20 percent deposit",paid["status"]=="SUCCESS" and float(paid["amount"])==float(early["advanceDeposit"]))
    replay=api("POST","/payments/demo-confirm",{"bookingId":early["id"]},d)
    check("Payment retry returns same payment ID",paid["id"]==replay["id"])
    api("POST","/merchant/check-in",{"bookingCode":early["bookingCode"],"parkingLotId":lid},m,400)
    check("Arrival earlier than 30 minutes is rejected by API",True)
    b=create(20)
    Path(".env.p0-session.json").write_text(json.dumps({"driver":driver,"merchant":merchant,"lotId":lid,"vehicleId":v["id"],"bookingId":b["id"],"earlyBookingId":early["id"]}))
    api("POST","/merchant/check-in",{"bookingCode":b["bookingCode"],"parkingLotId":lid},m,400)
    check("Unpaid booking cannot check in",True)
    api("POST","/payments/demo-confirm",{"bookingId":b["id"]},d)
    check("Paid deposit produces PENDING_CHECKIN",api("GET",f"/bookings/{b['id']}",token=d)["status"]=="PENDING_CHECKIN")
    api("POST","/merchant/check-in",{"bookingCode":b["bookingCode"],"parkingLotId":lid},m)
    check("Paid arrival within 30 minutes produces PARKING",api("GET",f"/bookings/{b['id']}",token=d)["status"]=="PARKING")
    out=api("POST","/merchant/check-out",{"bookingCode":b["bookingCode"],"parkingLotId":lid},m)
    check("Checkout calculates server amount and requires balance settlement",out["bookingStatus"]=="AWAITING_FINAL_PAYMENT" and float(out["amountDue"])==float(out["totalAmount"])-float(out["advanceDeposit"]))
    api("POST","/merchant/confirm-cash",{"bookingCode":b["bookingCode"],"parkingLotId":lid},m)
    check("Merchant confirms balance collected and booking completes",api("GET",f"/bookings/{b['id']}",token=d)["status"]=="COMPLETED")
    api("POST",f"/bookings/{early['id']}/cancel",{"reason":"Cleanup local acceptance fixture; demo funds only"},d)
    slots=api("GET",f"/parking-lots/{lid}/slots")
    check("Slots released after completion and cancellation",next(s for s in slots if s["vehicleType"]=="HEAVY_TRUCK")["availableCapacity"]==2)
    Path(".env.p0-session.json").write_text(json.dumps({"driver":driver,"merchant":merchant,"lotId":lid,"vehicleId":v["id"],"bookingId":b["id"]}))
    return {"lotId":lid,"completedBookingId":b["id"],"paymentMode":"explicit localhost DEMO; final settlement confirmed cash","checks":results}
if __name__=="__main__":
    try: report={"passed":True,**main()}
    except Exception as error: report={"passed":False,"error":str(error),"checks":results}
    Path("../docs/fe-be-integration/p0-local-api-results.json").write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
    print(json.dumps(report,ensure_ascii=True,indent=2))
    raise SystemExit(0 if report["passed"] else 1)
