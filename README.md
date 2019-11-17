# [Kiko Backend task](https://docs.google.com/document/d/10QsOiK5v1EV89bBylBRyRPs4-AkMBZS4_h0iRgIojyA/edit?usp=sharing)

<table>
<tr><td colspan="2"><h2>REST API</h2></td></tr>
<tr>
    <td colspan="2"><b>Tenant</b></td>
</tr>
<tr valign="top">
<td rowspan="2">
<code>GET /tenants</code><br>Retrieves a list of all tenants.
</td>
<td rowspan="2">
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
<tr></tr>
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


<tr>
    <td colspan="2"><b>Flat</b></td>
</tr>
<tr valign="top">
<td rowspan="2">
<code>GET /flats</code><br>Retrieves a list of all flats.
</td>
<td rowspan="2">
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
<tr></tr>
<tr valign="top">
<td rowspan="2">
<code>GET /flats/{flatId}</code><br>Returns a flat with the specified ID.
</td>
<td rowspan="2">
Response body:
<pre>{
        "id": number,
        "address": string,
        "currentTenantId": number
}</pre>
</td>
</tr>
<tr></tr>
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


<tr>
    <td colspan="2"><b>Reservation</b></td>
</tr>
<tr valign="top">
<td rowspan="2">
<code>GET /reservations?flatId={flatId}&from={from}&to={to}</code><br>Returns reservations that match the specified query criteria.
</td>
<td rowspan="2">
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
<tr></tr>
<tr valign="top">
<td rowspan="2">
<code>POST /reservations</code><br>Make a flat viewing reservation.
</td>
<td rowspan="2">
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
<tr></tr>
<tr valign="top">
<td rowspan="2">
<code>PUT /reservations/{reservationId}</code><br>Approves or rejects the specified reservation.
</td>
<td rowspan="2">
Request body:
<pre>{
    "approved": boolean
}</pre>
Returns HTTP status code 204 "No Content"
</td>
</tr>
<tr></tr>
<tr valign="top">
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
