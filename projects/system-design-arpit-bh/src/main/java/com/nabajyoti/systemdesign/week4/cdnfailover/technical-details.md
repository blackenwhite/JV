# CDN failover prototype

A small local lab. There is no real CDN vendor. There is no DNS change.
The 80/20 split happens when the backend writes a CDN URL into its response.

## Roles

- Origin 1 on port 8091. Same website as origin 2. Label in the HTML: origin-1.
- Origin 2 on port 8092. Same website as origin 1. Label in the HTML: origin-2.
- CDN on port 8081. Two properties on one process:
  - hostname cdn-a.localhost -> origin 1
  - hostname cdn-b.localhost -> origin 2
- Backend on port 8080. For each request, it chooses a CDN URL:
  80% use http://cdn-a.localhost:8081/...
  20% use http://cdn-b.localhost:8081/...

## Request path

client -> backend :8080
backend returns HTML that contains one CDN URL
client follows that URL -> CDN :8081
CDN reads the Host header, picks the property, fetches from that origin

## Package

com.nabajyoti.systemdesign.week4.cdnfailover

## What this lab is

Weighted URL selection. The backend chooses the hostname. The hostname
chooses the property. The property chooses the origin.

## What this lab is not

The CDN does not watch origin health. If origin 1 is down, most URLs
still point at cdn-a, so most requests still fail.