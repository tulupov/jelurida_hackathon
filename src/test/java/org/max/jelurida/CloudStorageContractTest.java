package org.max.jelurida;

import com.squareup.okhttp.*;
import nxt.addons.JO;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CloudStorageContractTest extends AbstractContractTest {

    @Test
    public void uploadDownloadTest() {
        String contractName = ContractTestHelper.deployContract(CloudStorageContract.class);
        executeContract(contractName);
    }

    void executeContract(String contractName) {
        try {
            JO response = uploadFile(contractName);
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(response.toJSONString());
            List<JO> transactions = response.getJoList("transactions");
            Assert.assertTrue(transactions.size() > 0);
            String hash = transactions.get(0).getString("fullHash");
            Assert.assertNotNull(hash);
            JO jo = downloadFile(contractName, hash);
            Assert.assertNotNull(jo);
            Assert.assertNotNull(jo.get("link"));
            System.out.println(jo.toJSONString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    JO uploadFile(String contractName) throws IOException {
        OkHttpClient client = new OkHttpClient();
        client.setConnectTimeout(3000, TimeUnit.SECONDS);
        client.setReadTimeout(3000, TimeUnit.SECONDS);
        RequestBody requestBody = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"contractName\""),
                        RequestBody.create(null, contractName))
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"ACCOUNT_CONTROL.json\""),
                        RequestBody.create(MediaType.parse("application/octet-stream"), Paths.get("./conf/data/ACCOUNT_CONTROL.json").toFile()))
                .build();

        Request request = new Request.Builder()
                .url("http://localhost:26876/nxt?requestType=triggerContractByRequest")
                .post(requestBody)
                .build();

        Response response = null;
        response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        return JO.parse(response.body().string());
    }

    JO downloadFile(String contractName, String hash) throws IOException {
        OkHttpClient client = new OkHttpClient();
        client.setConnectTimeout(300, TimeUnit.SECONDS);
        client.setReadTimeout(300, TimeUnit.SECONDS);
        Request request = new Request.Builder()
                .url("http://localhost:26876/nxt?requestType=triggerContractByRequest&contractName=" + contractName + "&txHash=" + hash)
                .build();
        Response response = null;
        response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        return JO.parse(response.body().string());
    }
}
