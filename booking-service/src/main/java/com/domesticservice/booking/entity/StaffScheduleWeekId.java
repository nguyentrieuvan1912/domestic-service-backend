package com.domesticservice.booking.entity;

import java.io.Serializable;
import java.time.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class StaffScheduleWeekId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long staffId;

    private LocalDate weekStart;

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof StaffScheduleWeekId that))
            return false;
        return Objects.equals(staffId, that.staffId) && Objects.equals(weekStart, that.weekStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staffId, weekStart);
    }
}
