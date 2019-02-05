package org.max.jelurida;

import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nxt.account.PaymentTransactionType;
import nxt.addons.JO;
import nxt.blockchain.Block;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.ChildTransactionType;
import nxt.blockchain.FxtTransaction;
import nxt.http.callers.TriggerContractByRequestCall;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.KeyChain.KeyPurpose;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.SendResult;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author maxtulupov@gmail.com
 */
public class Bitcoin2IgnisCurrencyExchangeTest extends AbstractContractTest {

    private WalletAppKit walletAppKit;
    private Wallet wallet;

    @Test
    public void bitcoin2IgnisExchangeTest() {
        String contractName = ContractTestHelper.deployContract(Bitcoin2IgnisCurrencyExchange.class);
        executeContract(contractName);
    }

    void executeContract(String contractName) {
        //start senders wallet
        runSendersBitcoinj();

        JO messageJson = new JO();
        messageJson.put("message", "ExchangeRequest");
        messageJson.put("contract", contractName);
        JO initialResponse = TriggerContractByRequestCall.create().contractName(contractName).call();
        String fundAddress = initialResponse.getString("fundAddress");
        Assert.assertNotNull(fundAddress);
        Double exchangeRate = (Double) initialResponse.get("exchangeRate");
        Assert.assertNotNull(exchangeRate);


        Sha256Hash sha256Hash = sendBitcoins(fundAddress);
        ECDSASignature ecdsaSignature = signHash(sha256Hash);
        BigInteger r = ecdsaSignature.r;
        BigInteger s = ecdsaSignature.s;

        //waiting while tx appears in bitcoin wallet.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        messageJson = new JO();
        messageJson.put("txHash", sha256Hash.toString());
        messageJson.put("signatureR", r.toString());
        messageJson.put("signatureS", s.toString());
        messageJson.put("exchangeRate", exchangeRate);
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        JO jo = ContractTestHelper.messageTriggerContract(message);
        generateBlock();

        // Verify that the contract create transaction
        Block lastBlock = getLastBlock();
        boolean isTxFound = false;
        for (FxtTransaction transaction : lastBlock.getFxtTransactions()) {
            for (ChildTransaction childTransaction : transaction.getSortedChildTransactions()) {
                if (ChildTransactionType
						.findTransactionType(childTransaction.getType().getType(), childTransaction.getType().getSubtype()) == PaymentTransactionType.ORDINARY)  {
                    isTxFound = true;
                    Assert.assertEquals(2, childTransaction.getChain().getId());
                    Assert.assertEquals(ALICE.getAccount().getId(), childTransaction.getSenderId());
                    Assert.assertEquals(BOB.getAccount().getId(), childTransaction.getRecipientId());
                    continue;
                }
            }
        }
        Assert.assertTrue(isTxFound);
    }

    private void runSendersBitcoinj() {
        walletAppKit = new WalletAppKit(TestNet3Params.get(),
                Paths.get(System.getProperty("java.io.tmpdir")).toFile(), "bitcoinj");
        walletAppKit.startAsync();
        try {
            walletAppKit.awaitRunning(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        if (!walletAppKit.isRunning()) {
            throw new RuntimeException("wallet is not ready");
        }
        wallet = walletAppKit.wallet();
        System.out.println("sender's wallet address = " + wallet.currentAddress(KeyPurpose.RECEIVE_FUNDS));
    }

    private Sha256Hash sendBitcoins(String address) {
//        if (true) {
//            Set<Transaction> transactions = wallet.getTransactions(false);
//            return new BigInteger("11501071333535148346976432469019006591898313345048357965711365466068828878658");
//        }
        SendRequest request = SendRequest.to(Address.fromBase58(TestNet3Params.get(), address),
                Coin.MILLICOIN);
        try {
            SendResult sendResult = wallet.sendCoins(request);
            return sendResult.tx.getHash();
        } catch (InsufficientMoneyException e) {
            throw new RuntimeException(e);
        }
    }

    private ECDSASignature signHash(Sha256Hash hash) {
        DeterministicKey deterministicKey = wallet.currentKey(KeyPurpose.RECEIVE_FUNDS);
        ECDSASignature sign = deterministicKey.sign(hash, null);
        return sign;
    }
}
