package service.store;

import dto.request.store.StoreRequest;
import dto.request.store.StorePaymentRequest;
import dto.response.product.ProductResponse;
import dto.response.store.StoreResponse;
import dto.response.store.StoreWithProductsResponse;
import entity.Order;
import entity.Product;
import entity.Store;
import entity.User;
import enums.OrderStatus;
import enums.ProductStatus;
import enums.StoreStatus;
import exception.AppException;
import exception.ErrorCode;
import exception.ResourceNotFoundException;
import mapper.StoreMapper;
import repository.order.OrderRepository;
import repository.product.ProductRepository;
import repository.store.StoreRepository;
import repository.user.UserRepository;
import service.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
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
        store.setStatus(StoreStatus.ACTIVE);
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

        // Check if store has ANY orders (active or completed)
        // If store has history, we cannot delete/deactivate it easily without affecting history reporting
        // Or we can allow deactivation but warn user. 
        // Requirement: "khi giao hàng thành công thì đã có dữ liệu trên database thì khi muốn xóa thì sẽ lỗi ràng buộc"
        // Implementing strict check: if ANY order exists, prevent delete.
        
        List<Order> orders = orderRepository.findByStoreId(storeId);
        if (!orders.isEmpty()) {
             // You might want to create a specific error code for this, 
             // but reusing CANNOT_DELETE_STORE_WITH_ACTIVE_ORDERS or a generic one is fine for now.
             // Or better: throw a clear message.
             throw new AppException(ErrorCode.CANNOT_DELETE_STORE_WITH_ACTIVE_ORDERS); 
        }

        // Soft delete by changing status
        store.setStatus(StoreStatus.INACTIVE);
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
        log.info("Getting all active stores");

        List<Store> stores = storeRepository.findByStatus(StoreStatus.ACTIVE);
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

        store.setStatus(status);
        store = storeRepository.save(store);

        return storeMapper.toStoreResponse(store);
    }

    @Override
    public List<StoreResponse> searchStores(String keyword) {
        log.info("Searching active stores with keyword: {}", keyword);

        List<Store> stores = storeRepository.findByNameContainingIgnoreCaseAndStatus(keyword, StoreStatus.ACTIVE);
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

        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new ResourceNotFoundException("Store not found (inactive) with id: " + storeId);
        }

        // Get all products of this store
        List<Product> products = productRepository.findByStoreIdAndStatus(storeId, ProductStatus.ACTIVE);

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

        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new ResourceNotFoundException("Store not found (inactive) with id: " + store.getId());
        }

        // Get all products of this store
        List<Product> products = productRepository.findByStoreIdAndStatus(store.getId(), ProductStatus.ACTIVE);

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
                .storeStatus(store.getStatus())
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
                .weightGram(product.getWeightGram())
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

        @Override
        @Transactional
        public StoreResponse updateStorePayment(Long storeId, StorePaymentRequest request) {
                log.info("Updating payment info for store: {}", storeId);

                Store store = storeRepository.findById(storeId)
                                .orElseThrow(() -> new AppException(ErrorCode.STORE_NOT_EXISTED));

                // Apply only non-null (and non-blank for Strings) fields
                if (request.getBankAccountName() != null && !request.getBankAccountName().isBlank()) {
                        store.setBankAccountName(request.getBankAccountName());
                }
                if (request.getBankAccountNumber() != null && !request.getBankAccountNumber().isBlank()) {
                        store.setBankAccountNumber(request.getBankAccountNumber());
                }
                if (request.getBankName() != null && !request.getBankName().isBlank()) {
                        store.setBankName(request.getBankName());
                }
                if (request.getBankBranch() != null && !request.getBankBranch().isBlank()) {
                        store.setBankBranch(request.getBankBranch());
                }
                if (request.getPayoutEmail() != null && !request.getPayoutEmail().isBlank()) {
                        store.setPayoutEmail(request.getPayoutEmail());
                }

                store = storeRepository.save(store);
                return storeMapper.toStoreResponse(store);
        }
}
