package vn.omnismart.store;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class StoreMemberId implements Serializable {

    private UUID storeId;
    private UUID userId;

    public StoreMemberId() {
    }

    public StoreMemberId(UUID storeId, UUID userId) {
        this.storeId = storeId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreMemberId that)) {
            return false;
        }
        return Objects.equals(storeId, that.storeId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, userId);
    }
}
