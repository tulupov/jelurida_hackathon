package org.max.jelurida;

import static nxt.blockchain.ChildChain.IGNIS;

import nxt.addons.*;
import nxt.blockchain.TransactionTypeEnum;
import nxt.http.callers.SendMoneyCall;
import nxt.util.Logger;
import java.math.BigInteger;

/**
 * @author maxtulupov@gmail.com
 */
public class Bitcoin2IgnisCurrencyExchange extends AbstractContract {

    @ContractParametersProvider
    interface Params {

        /**
         * minimal confirmations count to accept bitcoin tx
         */
        @ContractRunnerParameter
        int minimumBitcoinTxConfirmationsCount();

        /**
         * url for echange rates
         */
        @ContractSetupParameter
        default String coinApiUrl() {
            return "https://rest.coinapi.io/v1/exchangerate/BTC/IGNIS";
        }

        /**
         * coin api aceess key
         */
        @ContractSetupParameter
        default String coinApiKey() {
            return "BF708F23-E158-4BFE-B6C9-CE644DE202FF";
        }

        /**
         * bitcoin wallet address to fund money
         */
        @ContractRunnerParameter
        String bitcoinReceiverAddress();

        @ContractSetupParameter
        default String bitcoinApiUrl() {
            return "https://testnet.blockchain.info/";
        }
    }

    @Override
    public JO processRequest(RequestContext context) {
        Params params = context.getParams(Params.class);
        Double rate;
        try {
            rate = calcRate(params);
        } catch (Exception e) {
            Logger.logErrorMessage("Can not calculate exchange rate", e);
            return context.generateErrorResponse(20002, "Can not calculate exchange rate");
        }
        JO responseMsg = new JO();
        responseMsg.put("exchangeRate", rate);
        responseMsg.put("fundAddress", params.bitcoinReceiverAddress());
        return context.generateResponse(responseMsg);
    }

    @Override
    @ValidateTransactionType(accept = {TransactionTypeEnum.SEND_MESSAGE})
    @ValidateContractRunnerIsRecipient
    @ValidateChain(accept = {2})
    public JO processTransaction(TransactionContext context) {
        JO message;
        try {
            message = readMessage(context);
        } catch (Exception e) {
            Logger.logErrorMessage("Can't read BTC transaction hash", e);
            return context.generateErrorResponse(20000, "Can't read BTC transaction hash");
        }
        validateIncomingMessage(message);

        Params params = context.getParams(Params.class);
        Double totalSum;
        try {
            totalSum = new BitcoinJTransactionChecker(message, params).validateTxAndConvertAmount();
        } catch (Exception e) {
            Logger.logErrorMessage("Transaction doesn't exists, or don't have confirmations", e);
            return context.generateErrorResponse(20001, "Transaction doesn't exists, or don't have confirmations");
        }

        try {
            JO messageJson = new JO();
            messageJson.put("message", "Exchange tx" + message + ", totalSum = " + totalSum.longValue());
            JO jo = context.createTransaction(SendMoneyCall.create(context.getTransaction().getChainId())
                    .amountNQT(totalSum.longValue())
                    .feeNQT(IGNIS.ONE_COIN)
                    .message(messageJson.toJSONString())
                    .recipient(context.getTransaction().getSenderRs()));
            return jo;
        } catch (Exception e) {
            Logger.logErrorMessage("Error creating transaction", e);
            return context.generateErrorResponse(20003, "Error creating transaction");
        }
    }

    private Double calcRate(Params params) {
        return new CoinApiCurrencyRatesProvider().configure(params).rate();
    }

    private JO readMessage(TransactionContext context) {
        JO jo = context.getTransaction().getAttachmentJson();
        return JO.parse(jo.getString("message"));

    }

    private void validateIncomingMessage(JO message) {
        try {
            new BigInteger(message.getString("signatureR"));
            new BigInteger(message.getString("signatureS"));
            Double.valueOf(message.getString("exchangeRate"));
        } catch (Exception e) {
            throw new RuntimeException("Error parsing incoming message", e);
        }
        if (!message.containsKey("txHash")) {
            throw new RuntimeException("Error parsing incoming message");
        }
    }
}