param([int] $Port = 18997)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$base = 'http://127.0.0.1:' + $Port + '/paradigm/data-oriented/orders/preview'
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(8)
$good = '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30},{"sku":"Q","qty":1,"price":40}]}'
$tests = [Collections.Generic.List[object]]::new()
function AddTest([string] $name, [string] $body, [string] $query, [int] $want) {
    $tests.Add(@{name=$name;body=$body;query=$query;want=$want})
}
AddTest 'valid-default' $good '' 200
AddTest 'discount-zero' $good '?discountPercent=0' 200
AddTest 'discount-100' $good '?discountPercent=100' 200
AddTest 'discount-one' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":1,"price":1}]}' '?discountPercent=33' 200
AddTest 'discount-negative' $good '?discountPercent=-1' 422
AddTest 'discount-over-100' $good '?discountPercent=101' 422
AddTest 'discount-string' $good '?discountPercent=x' 400
AddTest 'discount-overflow' $good '?discountPercent=99999999999999999' 400
AddTest 'negative-qty' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30},{"sku":"Q","qty":-3,"price":40}]}' '' 422
AddTest 'missing-price' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30},{"sku":"Q","qty":1}]}' '' 422
AddTest 'missing-lines' '{"id":"A","status":"pending"}' '' 422
AddTest 'wrong-line-type' '{"id":"A","status":"pending","lines":[null]}' '' 422
AddTest 'qty-string' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":"2","price":30}]}' '' 422
AddTest 'qty-decimal' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2.5,"price":30}]}' '' 422
AddTest 'large-number' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":10001,"price":30}]}' '' 422
AddTest 'max-values' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":10000,"price":1000000}]}' '?discountPercent=50' 200
AddTest '20-lines-overflow-bound' ('{"id":"A","status":"pending","lines":[' + ((@('{"sku":"P","qty":10000,"price":1000000}')*20) -join ',') + ']}') '?discountPercent=50' 200
AddTest 'negative-price' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":1,"price":-1}]}' '' 422
AddTest 'wrong-status' '{"id":"A","status":"paid","lines":[{"sku":"P","qty":2,"price":30}]}' '' 422
AddTest 'nested-extra' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30,"currency":"USD"}]}' '' 422
AddTest 'root-extra' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":2,"price":30}],"customerSecret":"123"}' '' 422
AddTest 'empty-lines' '{"id":"A","status":"pending","lines":[]}' '' 422
AddTest 'oversize-lines' ('{"id":"A","status":"pending","lines":[' + ((@('{"sku":"P","qty":1,"price":1}')*21) -join ',') + ']}') '' 422
AddTest 'max-id-64' ('{"id":"' + ('A'*64) + '","status":"pending","lines":[{"sku":"P","qty":1,"price":1}]}') '' 200
AddTest 'id-over-64' ('{"id":"' + ('A'*65) + '","status":"pending","lines":[{"sku":"P","qty":1,"price":1}]}') '' 422
AddTest 'sku-over-64' ('{"id":"A","status":"pending","lines":[{"sku":"' + ('P'*65) + '","qty":1,"price":1}]}') '' 422
AddTest 'overlong-unknown-key' ('{"id":"A","status":"pending","lines":[{"sku":"P","qty":1,"price":1}],"'+('x'*1000)+'":false}') '' 422
AddTest 'malformed-json' '{"id":"A","lines":' '' 400
AddTest 'null-root' 'null' '' 400
AddTest 'array-root' '[]' '' 400
AddTest 'empty-object' '{}' '' 422
AddTest 'duplicate-field-json' '{"id":"A","status":"paid","status":"pending","lines":[{"sku":"P","qty":1,"price":1}]}' '' 400
AddTest 'duplicate-nested-json' '{"id":"A","status":"pending","lines":[{"sku":"P","qty":-3,"qty":2,"price":1}]}' '' 400
AddTest 'huge-id' ('{"id":"' + ('A'*10000) + '","status":"pending","lines":[{"sku":"P","qty":1,"price":1}]}') '' 422
$passed=0
foreach ($test in $tests) {
    $uri = $base + $test.query
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $uri)
    $req.Content = [System.Net.Http.StringContent]::new($test.body, [Text.Encoding]::UTF8, 'application/json')
    try {
        $res = $client.SendAsync($req).GetAwaiter().GetResult()
        $raw = $res.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $actual = [int]$res.StatusCode
        $parsed = $null
        try { $parsed = $raw | ConvertFrom-Json } catch {}
        if ($actual -ne $test.want) {
            throw "STATUS_MISMATCH name=$($test.name) expected=$($test.want) actual=$actual body=$($raw.Substring(0,[Math]::Min(250,$raw.Length)))"
        }
        $errors = if ($parsed -and $parsed.errors) { @($parsed.errors | ForEach-Object { $_.path + ':' + $_.actual }) -join ';' } else { '-' }
        if ($test.name -eq 'valid-default') {
            if ($parsed.before.lines[0].qty -ne 2 -or $parsed.after.lines[0].qty -ne 2 -or $parsed.observations.sumOfQuantityTimesPrice -ne 100 -or $parsed.after.discountedTotal -ne 90) { throw "Bad valid math/snapshots" }
        }
        if ($test.name -eq 'discount-zero' -and $parsed.after.discountedTotal -ne 100) {throw "Bad zero discount"}
        if ($test.name -eq 'discount-100' -and $parsed.after.discountedTotal -ne 0) {throw "Bad full discount"}
        if ($test.name -eq 'discount-one' -and $parsed.after.discountedTotal -ne 0.67) {throw "Bad two-decimal discount calculation"}
        if ($test.name -eq 'negative-qty' -and $errors -notmatch 'lines\[1\]\.qty') {throw "Missing nested qty path"}
        if ($test.name -eq 'missing-price' -and $errors -notmatch 'lines\[1\]\.price') {throw "Missing nested price path"}
        if ($test.name -eq 'max-values' -and $parsed.after.subtotal -ne 10000000000) {throw "Bad arithmetic boundary"}
        if ($test.name -eq '20-lines-overflow-bound' -and ($parsed.after.subtotal -ne 200000000000 -or $parsed.after.discountedTotal -ne 100000000000)) {throw "Bad twenty-line arithmetic"}
        if ($test.name -eq 'wrong-status' -and $errors -notmatch 'paid') {throw "Actual status value not reported"}
        if ($test.name -eq 'overlong-unknown-key' -and $errors.Length -gt 240) {throw "Oversized user-controlled path echoed"}
        if ($actual -eq 422 -and ($null -eq $parsed -or $parsed.accepted -ne $false -or $parsed.stage -ne 'boundaryValidation')) { throw "Schema error was not the expected validation rejection" }
        Write-Output "HTTP_R1_CASE $($test.name) $actual errors=$errors"
        $passed++
    } finally {
        if ($null -ne $res) { $res.Dispose(); $res=$null }
        $req.Dispose()
    }
}
Write-Output "HTTP_CONTRACT_ALL_PASS $passed CASES"
