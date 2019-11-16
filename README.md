# [Kiko Backend task](https://docs.google.com/document/d/10QsOiK5v1EV89bBylBRyRPs4-AkMBZS4_h0iRgIojyA/edit?usp=sharing)

## REST API

<table>
<tr style="background-color: #a15702; color: white">
    <td colspan="2"><b>Tenant</b></td>
</tr>
<tr valign="top" style="background-color: white">
<td>
<code>GET /tenants</code><br>Retrieves a list of all tenants.
</td>
<td>
Response body:
<pre>[
    {
        "id": number,
        "name": string
    },
    . . .
]</pre>
</td>
</tr>
<tr valign="top">
<td>
<code>POST /tenants</code><br>Creates a tenant.
</td>
<td>
Request body:
<pre>{
    "name": string
}</pre>
Response body:<br>
<code>number</code> - ID of a newly created tenant.
</td>
</tr>


<tr style="background-color: #a15702; color: white">
    <td colspan="2"><b>Flat</b></td>
</tr>
<tr valign="top" style="background-color: white">
<td>
<code>GET /flats</code><br>Retrieves a list of all flats.
</td>
<td>
Response body:
<pre>[
    {
        "id": number,
        "address": string,
        "currentTenantId": number
    },
    . . .
]</pre>
</td>
</tr>
<tr valign="top" style="background-color: white">
<td>
<code>GET /flats/{flatId}</code><br>Returns a flat with the specified ID.
</td>
<td>
Response body:
<pre>{
        "id": number,
        "address": string,
        "currentTenantId": number
}</pre>
</td>
</tr>
<tr valign="top">
<td>
<code>POST /flats</code><br>Creates a flat.
</td>
<td>
Request body:
<pre>{
    "address": string,
    "currentTenantId": number
}</pre>
Response body:<br>
<code>number</code> - ID of a newly created flat.
</td>
</tr>


<tr style="background-color: #a15702; color: white">
    <td colspan="2"><b>Reservation</b></td>
</tr>
<tr valign="top">
<td>
<code>GET /reservations?flatId={flatId}&from={from}&to={to}</code><br>Returns reservations that match the specified query criteria.
</td>
<td>
Response body:
<pre>[
    {
        "id": number,
        "flatId": number,
        "dateTime": string,
        "prospectiveTenantId": number,
        "approved": boolean
    },
    . . .
]</pre>
</td>
</tr>
<tr valign="top" style="background-color: white">
<td>
<code>POST /reservations</code><br>Make a flat viewing reservation.
</td>
<td>
Request body:
<pre>{
    "flatId": number,
    "dateTime": string,
    "prospectiveTenantId": number
}</pre>
Response body:<br>
<code>number</code> - ID of a newly created reservation.
</td>
</tr>
<tr valign="top">
<td>
<code>PUT /reservations/{reservationId}</code><br>Approves or rejects the specified reservation.
</td>
<td>
Request body:
<pre>{
    "approved": boolean
}</pre>
Returns HTTP status code 204 "No Content"
</td>
</tr>
<tr valign="top" style="background-color: white">
<td>
<code>DELETE /reservations/{reservationId}</code><br>Deletes (cancels) reservation.
</td>
<td>
Request body is empty.<br>
Returns HTTP status code 204 "No Content"
</td>
</tr>
</table>

## Build

Java 11 or newer is required.

`gradlew clean build`

## Run

1. `java -jar build\libs\fleduler-1.0-SNAPSHOT.jar`
1. Try `http://localhost:8081/tenants` in browser.
