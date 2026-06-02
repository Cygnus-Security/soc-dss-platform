package com.socdss.risk;

public record WhatIfRequest(
        String assetCriticality,
        String exposure,
        Integer alertCount,
        Integer maxRuleLevel,
        String mitreTactic,
        Boolean vulnerabilityContext
) {}
