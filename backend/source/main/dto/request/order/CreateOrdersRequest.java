package dto.request.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional request body for creating orders from cart.
 * Allows client to override delivery dropoff by specifying a saved addressId
 * or a custom map-picked location with lat/lng and display fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrdersRequest {
    // Use a saved address belonging to the current user
    private Long addressId;

    // Or provide a custom location
    private Double lat;
    private Double lng;
    private String fullAddress; // free text address to display
    private String label;       // e.g., "Nhà", "Cơ quan", "Khác"
}
