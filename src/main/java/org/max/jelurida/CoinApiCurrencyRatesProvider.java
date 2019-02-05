package org.max.jelurida;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import nxt.addons.JO;


/**
 * @author maxtulupov@gmail.com
 */
public class CoinApiCurrencyRatesProvider implements CurrencyRatesProvider {

    private String apiKey;

    private String apiUrl;

    @Override
    public CurrencyRatesProvider configure(Bitcoin2IgnisCurrencyExchange.Params params) {
        apiKey = params.coinApiKey();
        apiUrl = params.coinApiUrl();
        return this;
    }

    @Override
    public Double rate() {
        InputStreamReader reader = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestProperty("X-CoinAPI-Key", apiKey);
            connection.connect();
            if (connection.getResponseCode() == 200) {
                reader = new InputStreamReader(connection.getInputStream());
				JO jo = JO.parse(reader);
				return (Double) jo.get("rate");
            } else {
                throw new RuntimeException("Can't calculate exchange rate");
            }
        }catch (IOException e) {
            throw new RuntimeException("Can't calculate exchange rate", e);
        } finally {
            if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {}
			}
        }
    }
}