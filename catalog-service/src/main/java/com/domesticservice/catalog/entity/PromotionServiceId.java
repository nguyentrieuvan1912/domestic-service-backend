package com.domesticservice.catalog.entity;

import java.io.Serializable;
import java.time.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class PromotionServiceId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long promotionId;

    private Long serviceId;

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof PromotionServiceId that))
            return false;
        return Objects.equals(promotionId, that.promotionId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promotionId, serviceId);
    }
}
