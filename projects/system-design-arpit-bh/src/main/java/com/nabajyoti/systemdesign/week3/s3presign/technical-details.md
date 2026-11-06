# S3 pre-signed URL — Technical Details

A local prototype that builds an S3 pre-signed URL in plain Java, following AWS Signature Version 4 query-string auth. There is no AWS SDK. MinIO on Docker checks the URL the same way S3 would.

**Lab directory:** `projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/s3presign/`

This is a learning lab. `minioadmin` / `minioadmin` is the published MinIO default. Do not reuse it outside this machine.

---

## What we are building

S3 only speaks HTTP. A download is `GET`. An upload is `PUT`. Signing happens where the secret key is. S3 (here, MinIO) only checks.

The server does not register the URL. The URL is the whole message. MinIO reads the access key id from `X-Amz-Credential`, looks up its own copy of the secret, rebuilds the canonical request, recomputes the HMAC, and compares it to `X-Amz-Signature`. It also rejects the call when `X-Amz-Date` plus `X-Amz-Expires` is past.

```text
Java process (holds secret)
  -> four SigV4 tasks
  -> URL with X-Amz-* query params
curl / browser (no secret)
  -> GET or PUT that URL
MinIO :9000
  -> rebuild signature, compare, serve or 403
```

---

## Docs this lab follows

The S3 API welcome page is only a cover sheet: [Amazon S3 API Welcome](https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html).

| Topic | Page |
| --- | --- |
| Idea | [Using presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) |
| Query-string signing | [Authenticating Requests: Using Query Parameters](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-query-string-auth.html) |
| Task 1 | [Create a canonical request](https://docs.aws.amazon.com/general/latest/gr/sigv4-create-canonical-request.html) |
| Task 2 | [Create a string to sign](https://docs.aws.amazon.com/general/latest/gr/sigv4-create-string-to-sign.html) |
| Task 3 | [Calculate the signature](https://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html) |
| Task 4 | [Add the signature to the request](https://docs.aws.amazon.com/general/latest/gr/sigv4-add-signature-to-request.html) |

---

## Prerequisites

- Docker Desktop (or Docker Engine + Compose v2)
- JDK 23 (this Gradle project already uses Java 23)
- `curl`
- Run `PresignedGet` from the IDE, or compile it with the rest of `system-design-arpit-bh`

No AWS account. No extra Java library. Signing uses `Mac` and `MessageDigest` from the JDK.

---

## Locked choices

- Path-style object URI: `/uploads/hello.txt` (bucket in the path, not in the host).
- Host header: `localhost:9000` (port included because it is not 80 or 443).
- Region in the credential scope: `us-east-1` (MinIO default).
- Payload hash: `UNSIGNED-PAYLOAD` (body is not part of the signature).
- Official `minio/minio` and `quay.io/minio/minio` images are gone or refuse anonymous pulls. This compose file uses `ghcr.io/coollabsio/minio:latest`.

---

## Steps

### 1. Picture the request

Read the four signing-task pages above. Connect them to this model:

1. **Canonical request** — one exact string for the HTTP call (method, path, sorted query without the signature, headers, payload hash). MinIO rebuilds this later. One different byte and the check fails.
2. **String to sign** — algorithm, `X-Amz-Date`, credential scope `date/region/s3/aws4_request`, and the hex SHA-256 of the canonical request.
3. **Signature** — HMAC-SHA256 of that string, using a key derived from `"AWS4" + secret`. This is the only step that touches the secret.
4. **Attach it** — add `X-Amz-Signature` to the URL. The client just sends `GET` or `PUT`.

### 2. Start MinIO and put one private object in it

From this directory:

```bash
docker compose up -d
```

Open http://localhost:9001 and sign in with `minioadmin` / `minioadmin`.

- Create bucket `uploads`.
- Upload object `hello.txt` with body `hello from minio` (a copy of that file sits next to this doc).

Port 9000 is the S3 API. Port 9001 is the console.

Confirm an unsigned request is refused:

```bash
curl -i http://localhost:9000/uploads/hello.txt
```

Expect `403` and `AccessDenied`. `404` / `NoSuchKey` means the bucket or object name does not match.

If `quay.io/minio/minio` or `minio/minio` fails to pull (`401` or `pull access denied`), keep the Coolify image already in `docker-compose.yml`. That is not a `docker login` problem; the official public images were removed.

### 3. Canonical request (task 1)

`PresignedGet` builds this multiline string:

```text
GET
/uploads/hello.txt
<sorted, AWS-encoded query without X-Amz-Signature>
host:localhost:9000

host
UNSIGNED-PAYLOAD
```

Details that must match MinIO:

- Query names are sorted (`TreeMap`). Typical order: `X-Amz-Algorithm`, `X-Amz-Credential`, `X-Amz-Date`, `X-Amz-Expires`, `X-Amz-SignedHeaders`.
- `X-Amz-Signature` is **not** in this string. It is appended after signing.
- Encoding is AWS’s, not `URLEncoder`. Unreserved characters stay as-is; everything else is `%XX`. Slashes in the credential become `%2F`.
- Each canonical header ends with `\n`, and the next field is joined with another `\n`, so there is a blank line after `host:localhost:9000`.

### 4. String to sign (task 2)

Four lines:

```text
AWS4-HMAC-SHA256
<X-Amz-Date>
<dateStamp>/<region>/s3/aws4_request
<lowercase hex SHA-256 of the entire canonical request>
```

The date, region, and service in this scope must match `X-Amz-Credential` in the query. Hex is lowercase (`%02x`).

### 5. Signing key and signature (task 3)

Four HMAC-SHA256 rounds, then HMAC of the string to sign:

```text
kDate    = HMAC("AWS4" + secret, dateStamp)
kRegion  = HMAC(kDate, region)
kService = HMAC(kRegion, "s3")
kSigning = HMAC(kService, "aws4_request")
signature = hex(HMAC(kSigning, stringToSign))
```

Each round’s key is raw bytes, not hex. Only the final signature is printed as 64 lowercase hex characters. The prefix `AWS4` is required. The secret never goes in the query; only the access key id does.

### 6. Attach the signature and download (task 4)

```text
http://localhost:9000/uploads/hello.txt?<canonicalQuery>&X-Amz-Signature=<hex>
```

Quote the URL for `curl`. The query contains `&`.

```bash
curl -i 'PASTE_THE_GET_URL'
```

Expect `200` and `hello from minio`.

A frozen `X-Amz-Date` plus a 600-second expiry will produce `403` with `Request has expired` once the clock moves past that window. The HTTP code is still `AccessDenied`; the XML `Message` is the useful line. Recompute the whole signature with a current UTC timestamp. Do not edit only the date on an old URL.

### 7. Signed PUT, short expiry, and what to tighten

`PresignedGet.main` prints two URLs built with `Instant.now()`:

- `PUT /uploads/from-curl.txt` lasting 600 seconds
- `GET /uploads/from-curl.txt` lasting 5 seconds

Run `PresignedGet`, then:

```bash
curl -i -X PUT --data 'uploaded via presigned url' 'PASTE_PUT_URL'
curl -i 'PASTE_GET_URL'
```

Expect `200` on the PUT, then `200` and `uploaded via presigned url` on the GET.

Wait six seconds and curl the same GET URL again. Expect `403` and `Request has expired`.

A GET URL cannot upload. A PUT URL cannot download. The method is inside the canonical request.

In a real service:

- Keep lifetimes short. The URL is the credential.
- Do not log the URL.
- Keep the secret on the server.
- Sign the headers you mean to lock (for uploads, often `content-type`).
- Use HTTPS and a real key on AWS. `http://localhost:9000` and `minioadmin` are lab-only.

---

## How to run the finished class

1. `docker compose up -d` in this directory.
2. Create bucket `uploads` and object `hello.txt` if this is a fresh volume.
3. Run `com.nabajyoti.systemdesign.week3.s3presign.PresignedGet`.
4. `curl` the printed PUT URL, then the GET URL, then wait and curl the GET URL again.

---

## Failures seen in this lab

| Symptom | Cause |
| --- | --- |
| `quay.io/minio/minio` `401 UNAUTHORIZED` | Quay refuses anonymous pulls of that image |
| `pull access denied for minio/minio` | Official Docker Hub repo is gone; not a login issue |
| Unsigned GET → `403 AccessDenied` | Object is private; expected before signing |
| Signed GET → `403` `Request has expired` | `X-Amz-Date` + `X-Amz-Expires` is in the past |
| `403 SignatureDoesNotMatch` | Canonical request differs (blank line, host port, `%2F`, or signature included in the hash) |
| `404 NoSuchKey` | Bucket or object name does not match |

---

## Files

```text
s3presign/
├── docker-compose.yml     # MinIO API :9000, console :9001
├── hello.txt              # seed object body
├── PresignedGet.java      # SigV4 query-string signer
└── technical-details.md   # this file
```
