package service.store;

import dto.request.store.StoreRequest;
import dto.response.store.StoreResponse;
import dto.response.store.StoreWithProductsResponse;
import enums.StoreStatus;

import java.util.List;

public interface StoreService {

    /**
     * Create new store
     */
    StoreResponse createStore(StoreRequest request);

    /**
     * Update store information
     */
    StoreResponse updateStore(Long storeId, StoreRequest request);

    /**
     * Delete store (soft delete by changing status)
     */
    void deleteStore(Long storeId);

    /**
     * Get store by ID
     */
    StoreResponse getStoreById(Long storeId);

    /**
     * Get all stores
     */
    List<StoreResponse> getAllStores();

    /**
     * Get stores by owner user ID
     */
    List<StoreResponse> getStoresByOwner(Long ownerUserId);

    /**
     * Change store status
     */
    StoreResponse changeStoreStatus(Long storeId, StoreStatus status);

    /**
     * Search stores by name
     */
    List<StoreResponse> searchStores(String keyword);

    /**
     * Get store information with all products by store ID
     */
    StoreWithProductsResponse getStoreWithProducts(Long storeId);

    /**
     * Get store information with products by product ID
     * (Find store that sells the given product)
     */
    StoreWithProductsResponse getStoreWithProductsByProductId(Long productId);

    /**
     * Create product for a store
     */
    dto.response.product.ProductResponse createProductForStore(Long storeId, dto.request.product.ProductRequest request);

    /**
     * Update product for a store
     */
    dto.response.product.ProductResponse updateProductForStore(Long storeId, Long productId, dto.request.product.ProductRequest request);

    /**
     * Delete product for a store
     */
    void deleteProductForStore(Long storeId, Long productId);
}

