package org.max.jelurida;

/**
 * @author maxtulupov@gmail.com
 */
interface CurrencyRatesProvider {

    /**
     * API config, setup auth tokens, etc.
     */
    CurrencyRatesProvider configure(Bitcoin2IgnisCurrencyExchange.Params params);

    /**
     * Calculates current rate between BTC and IGNIS
     */
    Double rate();
}