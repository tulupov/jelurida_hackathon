package org.max.jelurida;

import nxt.addons.*;
import nxt.http.callers.DownloadTaggedDataCall;
import nxt.http.callers.UploadTaggedDataCall;
import nxt.util.Logger;

import static nxt.blockchain.ChildChain.IGNIS;

/**
 * @author maxtulupov@gmail.com
 */
public class CloudStorageContract extends AbstractContract {

	@ContractParametersProvider
	interface Params {

		@ContractSetupParameter
		default String dropboxContentUrl() {
			return "https://content.dropboxapi.com/2/files";
		}

		@ContractSetupParameter
		default String dropboxApiUrl() {
			return "https://api.dropboxapi.com/2/files";
		}

		@ContractSetupParameter
		default String dropboxAccessToken() {
			return "Bearer n7jqdIXUC3YAAAAAAAAIC0yDuuxaEoDmcDph2RQ5g2smGLqBo2hEMN8yrp94y1Xg";
		}
	}

	@Override
	public JO processRequest(RequestContext requestContext) {
		Params params = requestContext.getParams(Params.class);
		if ("POST".equalsIgnoreCase(requestContext.getRequest().getMethod()) || "PUT".equalsIgnoreCase(requestContext.getRequest().getMethod())) {
			try {
				JO result = new DropboxCloudStorage(params).upload(requestContext);
				UploadTaggedDataCall uploadTaggedDataCall = UploadTaggedDataCall.create(IGNIS.getId())
						.data(result.toJSONString())
						.name(result.getString("filename"))
						.feeNQT(IGNIS.ONE_COIN)
						.secretPhrase(requestContext.getConfig().getSecretPhrase());
				return requestContext.createTransaction(uploadTaggedDataCall);
			} catch (Exception e) {
				return generateErrorResponse(requestContext, e);
			}
		}
		if ("GET".equalsIgnoreCase(requestContext.getRequest().getMethod())) {
			String txHash = requestContext.getParameter("txHash");
			if (txHash == null || txHash.isEmpty()) {
				return requestContext.generateErrorResponse(30002, "txHash parameter missing");
			}

			try {
				JO result =  DownloadTaggedDataCall
						.create(IGNIS.getId()).retrieve(true).transactionFullHash(txHash).build().getJsonResponse();
				String link = new DropboxCloudStorage(params).getLink(result);
				JO response = new JO();
				response.put("message", "The following link available for the next four hours");
				response.put("link", link);
				return requestContext.generateResponse(response);
			} catch (Exception e) {
				return generateErrorResponse(requestContext, e);
			}
		}
		return requestContext.generateErrorResponse(30003, "Only GET, POST, PUT method supported");
	}

	private JO generateErrorResponse(AbstractContractContext context, Throwable ex) {
		Logger.logErrorMessage(ex.getMessage(), ex);
		if (ex instanceof ApplicationException) {
			return context.generateErrorResponse(((ApplicationException) ex).getErrorCode(), ex.getMessage());
		}
		return context.generateErrorResponse(66666, "Unexpected error occurred");
	}
}
