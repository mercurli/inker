package com.inker.backend.service.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EastMoneyCompanyProfileProviderTest {

    private final EastMoneyCompanyProfileProvider provider = new EastMoneyCompanyProfileProvider();

    @Test
    void toEastMoneyCode_shouldUseExchangeCodeWhenAvailable() {
        assertEquals("SH600000", provider.toEastMoneyCode("600000", "SSE"));
        assertEquals("SZ000001", provider.toEastMoneyCode("000001", "SZSE"));
    }

    @Test
    void toEastMoneyCode_shouldInferMarketFromStockCode() {
        assertEquals("SH600000", provider.toEastMoneyCode("600000", null));
        assertEquals("SZ300001", provider.toEastMoneyCode("300001", null));
        assertNull(provider.toEastMoneyCode("830001", null));
    }
}
