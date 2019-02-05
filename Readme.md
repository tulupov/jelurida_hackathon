
example configuration in conf/contracts.json file

Contract requires additional permission.

It should be added to codeBase "file://untrustedContractCode"

permission java.lang.reflect.ReflectPermission "newProxyInPackage.org.max.jelurida";

Tried to add permissions to a specific contract like in docs https://ardordocs.jelurida.com/Lightweight_Contracts
But it doesn't work for me.

Contract implements the currency exchange between user A and user B. The flow below.

1. User A sends api request to contract running on user B machine.
Expected payload

 JO messageJson = new JO();
 messageJson.put("message", "ExchangeRequest");
 messageJson.put("contract", contractName);
 
 Contract respond with
 {
 "fundAddress":"base58 bitcoin wallet address",
 "exchangeRate":exchange rate - double
 }
 
 2. User A sends bitcoin to the address from step 2.
 
 3. User A sign transaction hash and sends message to contract
 Expected payload
 
 messageJson = new JO();
 messageJson.put("txHash", sha256Hash.toString());
 messageJson.put("signatureR", r.toString());
 messageJson.put("signatureS", s.toString());
 messageJson.put("exchangeRate", exchangeRate);
 messageJson.put("contract", contractName);
 
 txHash - bitcoin tx hash as BigInteger
 signatureR, signatureS - ECDSASignature r and s as BigInteger
 exchangeRate - rate from step 1. I know this is incorrect, rate must be saved on the contract side,
 probably one of the places - account preferences. But I left it as is.
 
 4. Contract validates bitcoin tx, and send IGNIS to User A.
 
 