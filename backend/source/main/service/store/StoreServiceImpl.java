package service.store;

import dto.request.store.StoreRequest;
import dto.response.product.ProductResponse;
import dto.response.store.StoreResponse;
import dto.response.store.StoreWithProductsResponse;
import entity.Product;
import entity.Store;
import entity.User;
import enums.ProductStatus;
import enums.StoreStatus;
import exception.AppException;
import exception.ErrorCode;
import exception.ResourceNotFoundException;
import mapper.StoreMapper;
import repository.product.ProductRepository;
import repository.store.StoreRepository;
import repository.user.UserRepository;
import service.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
        private final StoreMapper storeMapper;
        private final ProductService productService;

    @Override
    @Transactional
    public StoreResponse createStore(StoreRequest request) {
        log.info("Creating store for owner: {}", request.getOwnerUserId());

        // Validate user exists
        User owner = userRepository.findById(request.getOwnerUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Store store = storeMapper.toStore(request);
        store.setStoreStatus(StoreStatus.ACTIVE);
        store = storeRepository.save(store);

        return storeMapper.toStoreResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse updateStore(Long storeId, StoreRequest request) {
        log.info("Updating store: {}", storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Update fields
        storeMapper.updateStore(store, request);
        store = storeRepository.save(store);

        return storeMapper.toStoreResponse(store);
    }

    @Override
    @Transactional
    public void deleteStore(Long storeId) {
        log.info("Deleting store: {}", storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Soft delete by changing status
        store.setStoreStatus(StoreStatus.INACTIVE);
        storeRepository.save(store);
    }

    @Override
    public StoreResponse getStoreById(Long storeId) {
        log.info("Getting store: {}", storeId);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        return storeMapper.toStoreResponse(store);
    }

    @Override
    public List<StoreResponse> getAllStores() {
        log.info("Getting all stores");

        List<Store> stores = storeRepository.findAll();
        return stores.stream()
                .map(storeMapper::toStoreResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<StoreResponse> getStoresByOwner(Long ownerUserId) {
        log.info("Getting stores by owner: {}", ownerUserId);

        // Validate user exists
        userRepository.findById(ownerUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<Store> stores = storeRepository.findByOwnerUserId(ownerUserId);
        return stores.stream()
                .map(storeMapper::toStoreResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StoreResponse changeStoreStatus(Long storeId, StoreStatus status) {
        log.info("Changing store {} status to {}", storeId, status);

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        store.setStoreStatus(status);
        store = storeRepository.save(store);

        return storeMapper.toStoreResponse(store);
    }

    @Override
    public List<StoreResponse> searchStores(String keyword) {
        log.info("Searching stores with keyword: {}", keyword);

        List<Store> stores = storeRepository.findByNameContainingIgnoreCase(keyword);
        return stores.stream()
                .map(storeMapper::toStoreResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public StoreWithProductsResponse getStoreWithProducts(Long storeId) {
        log.info("Getting store with products for store ID: {}", storeId);

        // Get store information
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + storeId));

        // Get all products of this store
        List<Product> products = productRepository.findByStoreId(storeId);

        return buildStoreWithProductsResponse(store, products);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreWithProductsResponse getStoreWithProductsByProductId(Long productId) {
        log.info("Getting store with products for product ID: {}", productId);

        // Get product to find store
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        // Get store information
        Store store = storeRepository.findById(product.getStore().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + product.getStore().getId()));

        // Get all products of this store
        List<Product> products = productRepository.findByStoreId(store.getId());

        return buildStoreWithProductsResponse(store, products);
    }

    private StoreWithProductsResponse buildStoreWithProductsResponse(Store store, List<Product> products) {
        // Convert products to ProductResponse
        List<ProductResponse> productResponses = products.stream()
                .map(this::convertToProductResponse)
                .collect(Collectors.toList());

        // Count available products
        long availableCount = products.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE && p.getQuantityAvailable() > 0)
                .count();

        return StoreWithProductsResponse.builder()
                .id(store.getId())
                .ownerUserId(store.getOwnerUserId())
                .name(store.getName())
                .description(store.getDescription())
                .bankAccountName(store.getBankAccountName())
                .bankAccountNumber(store.getBankAccountNumber())
                .bankName(store.getBankName())
                .bankBranch(store.getBankBranch())
                .payoutEmail(store.getPayoutEmail())
                .storeStatus(store.getStoreStatus())
                .createdAt(store.getCreatedAt())
                .updatedAt(store.getUpdatedAt())
                .products(productResponses)
                .totalProducts(products.size())
                .availableProducts((int) availableCount)
                .build();
    }

    private ProductResponse convertToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .storeId(product.getStore().getId())
                .categoryId(product.getCategory().getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .currency(product.getCurrency())
                .mediaPrimaryUrl(product.getMediaPrimaryUrl())
                .quantityAvailable(product.getQuantityAvailable())
                .reservedQuantity(product.getReservedQuantity())
                .safetyStock(product.getSafetyStock())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public dto.response.product.ProductResponse createProductForStore(
            Long storeId,
            dto.request.product.ProductRequest request) {
        log.info("Creating product for store: {}", storeId);

        // Validate store exists
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Ensure request has correct storeId
        request.setStoreId(storeId);

        // Delegate to ProductService for actual creation
        return productService.create(request);
    }

    @Override
    @Transactional
    public dto.response.product.ProductResponse updateProductForStore(
            Long storeId,
            Long productId,
            dto.request.product.ProductRequest request) {
        log.info("Updating product {} for store: {}", productId, storeId);

        // Validate store exists
        storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Validate product exists and belongs to this store
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_EXISTED));

        if (!product.getStore().getId().equals(storeId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Ensure request has correct storeId
        request.setStoreId(storeId);

        // Delegate to ProductService for actual update
        return productService.update(productId, request);
    }

    @Override
    @Transactional
    public void deleteProductForStore(Long storeId, Long productId) {
        log.info("Deleting product {} for store: {}", productId, storeId);

        // Validate store exists
        storeRepository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

        // Validate product exists and belongs to this store
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_EXISTED));

        if (!product.getStore().getId().equals(storeId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Delegate to ProductService for actual deletion
        productService.delete(productId);
    }
}
