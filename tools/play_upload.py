#!/usr/bin/env python3
"""Upload an AAB to the Play alpha track as a draft release (plan-B for GPP).
Auth: service-account JSON at $PLAY_SA_KEY_FILE. Usage: play_upload.py <aab-path>"""
import os, sys
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.cliplist.app"
creds = service_account.Credentials.from_service_account_file(
    os.environ["PLAY_SA_KEY_FILE"],
    scopes=["https://www.googleapis.com/auth/androidpublisher"])
pub = build("androidpublisher", "v3", credentials=creds)
edit = pub.edits().insert(packageName=PACKAGE).execute()
eid = edit["id"]
bundle = pub.edits().bundles().upload(
    packageName=PACKAGE, editId=eid,
    media_body=MediaFileUpload(sys.argv[1], mimetype="application/octet-stream",
                               chunksize=-1, resumable=True)).execute()
vc = bundle["versionCode"]
pub.edits().tracks().update(
    packageName=PACKAGE, editId=eid, track="alpha",
    body={"track": "alpha",
          "releases": [{"status": "draft", "versionCodes": [str(vc)]}]}).execute()
print(pub.edits().commit(packageName=PACKAGE, editId=eid).execute())
