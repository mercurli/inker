package com.inker.backend.service.provider;

import java.util.Optional;

public interface CompanyProfileProvider {
    Optional<String> fetchCompanyWebsite(String code, String exchangeCode);
}
