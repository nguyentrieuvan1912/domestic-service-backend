package com.domesticservice.ai.controller;

import com.domesticservice.ai.service.CatalogSemanticRecommendations;
import com.domesticservice.common.web.ServiceHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai-assistant/recommendations")
class SemanticRecommendationController {
    private final CatalogSemanticRecommendations recommendations;
    private final ServiceHttpClient identity;

    SemanticRecommendationController(CatalogSemanticRecommendations recommendations,
                                     @Value("${clients.identity.base-url}") String identityUrl) {
        this.recommendations = recommendations;
        identity = new ServiceHttpClient(identityUrl);
    }

    @PostMapping
    Object recommend(@RequestHeader(value="Authorization", required=false) String token,
                     @RequestBody CatalogSemanticRecommendations.Query request) {
        identity.authenticate(token, "CUSTOMER");
        return recommendations.recommend(request);
    }
}
