package vn.omnismart.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import vn.omnismart.audit.AuditLogService;
import vn.omnismart.store.StoreAuthorizationService;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreOperationGuard;
import vn.omnismart.store.StoreRole;

class ProductPublicationGuardTests {

    @Test
    void activePublishingJobBlocksArchiveBeforeAnyMutationOrAudit() {
        ProductRepository productRepository = mock(ProductRepository.class);
        StoreOperationGuard storeOperationGuard = mock(StoreOperationGuard.class);
        StoreAuthorizationService authorization = mock(StoreAuthorizationService.class);
        AuditLogService audit = mock(AuditLogService.class);
        ProductPublicationGuard publicationGuard = mock(ProductPublicationGuard.class);
        ProductService service = new ProductService(
                productRepository, authorization, storeOperationGuard, audit, publicationGuard);

        UUID storeId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        OidcUser principal = mock(OidcUser.class);
        Product product = new Product(
                productId, storeId, "PUBLISH-1", "Publishing", null,
                BigDecimal.ONE, ProductCurrency.VND, 1);
        when(authorization.requireMembership(principal, storeId))
                .thenReturn(new StoreMember(storeId, actorId, StoreRole.OWNER));
        when(productRepository.findByIdAndStoreId(productId, storeId)).thenReturn(Optional.of(product));
        when(publicationGuard.hasActivePublishingJobs(storeId, productId)).thenReturn(true);

        assertThatThrownBy(() -> service.archive(principal, storeId, productId, 0, "PUBLISH-1"))
                .isInstanceOf(ProductCatalogException.class)
                .extracting("code")
                .isEqualTo("PRODUCT_HAS_ACTIVE_PUBLISHING_JOBS");
        org.assertj.core.api.Assertions.assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        verifyNoInteractions(audit);
    }
}
