package com.domesticservice.identity.entity;

import java.io.Serializable;
import java.time.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class CustomerFavoriteId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long customerId;

    private Long serviceId;

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof CustomerFavoriteId that))
            return false;
        return Objects.equals(customerId, that.customerId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, serviceId);
    }
}
