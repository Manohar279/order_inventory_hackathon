package com.hackathon.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.orders.dto.OrderItemRequest;
import com.hackathon.orders.dto.OrderRequest;
import com.hackathon.orders.entity.OrderStatus;
import com.hackathon.orders.repository.InventoryRepository;
import com.hackathon.orders.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the mandatory scenarios. Each test gets a fresh Spring context,
 * so the H2 database is re-seeded and tests never depend on each other's stock changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("TC01: 101x1 + 102x2 is CONFIRMED, total 51600, stock drops by 1 and 2")
    void tc01_validOrderIsConfirmed() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"CUST001","items":[{"productId":101,"quantity":1},{"productId":102,"quantity":2}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1001))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(51600.0))
                .andExpect(jsonPath("$.message").value("Order confirmed successfully"));

        assertThat(stockOf(101L)).isEqualTo(9);
        assertThat(stockOf(102L)).isEqualTo(23);

        mockMvc.perform(get("/api/audit/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("ORDER_CONFIRMED"));
    }

    @Test
    @DisplayName("TC02: 105x1 (zero stock) is REJECTED, nothing deducted, still retrievable and audited")
    void tc02_outOfStockIsRejected() throws Exception {
        String body = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"CUST001","items":[{"productId":105,"quantity":1}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Insufficient stock for product 105")))
                .andReturn().getResponse().getContentAsString();

        long orderId = objectMapper.readTree(body).get("orderId").asLong();
        assertThat(stockOf(105L)).isZero();

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/audit/" + orderId))
                .andExpect(jsonPath("$[0].action").value("ORDER_REJECTED"));
    }

    @Test
    @DisplayName("TC03: unknown product id returns 400 and inventory is unchanged")
    void tc03_unknownProductIs400() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"CUST001","items":[{"productId":101,"quantity":1},{"productId":999,"quantity":1}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]").value("productId 999 does not exist"));

        assertThat(stockOf(101L)).isEqualTo(10);
    }

    @Test
    @DisplayName("TC04: GET a created order returns correct items, amount and status")
    void tc04_getOrder() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customerId":"CUST001","items":[{"productId":101,"quantity":1},{"productId":102,"quantity":2}]}
                """)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST001"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(51600.0))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId").value(101))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[1].productId").value(102))
                .andExpect(jsonPath("$.items[1].quantity").value(2));

        mockMvc.perform(get("/api/orders/99999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Multi-item order with ONE out-of-stock item is rejected with NO partial deduction")
    void mixedOrderDoesNotPartiallyDeduct() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId":"CUST002","items":[{"productId":101,"quantity":1},{"productId":103,"quantity":2},{"productId":105,"quantity":1}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(stockOf(101L)).isEqualTo(10);
        assertThat(stockOf(103L)).isEqualTo(15);
        assertThat(stockOf(105L)).isZero();
    }

    @Test
    @DisplayName("Validation: missing customer, empty items, non-positive quantity all return 400")
    void invalidInputIs400() throws Exception {
        String[] badBodies = {
                "{\"items\":[{\"productId\":101,\"quantity\":1}]}",
                "{\"customerId\":\"CUST001\",\"items\":[]}",
                "{\"customerId\":\"CUST001\",\"items\":[{\"productId\":101,\"quantity\":0}]}",
                "{\"customerId\":\"CUST001\",\"items\":[{\"productId\":101,\"quantity\":-3}]}",
                "{\"customerId\":\"CUST001\",\"items\":[{\"quantity\":1}]}",
                "{ not json"
        };
        for (String body : badBodies) {
            mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(stockOf(101L)).isEqualTo(10);
    }

    @Test
    @DisplayName("Bonus: 10 concurrent orders for 1 monitor each (stock 5) -> exactly 5 confirmed, stock 0")
    void concurrentOrdersNeverOversell() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OrderStatus>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String customer = "CUST-" + i;
            Callable<OrderStatus> task = () -> {
                start.await();
                return orderService.placeOrder(new OrderRequest(customer,
                        List.of(new OrderItemRequest(104L, 1)), null)).order().status();
            };
            results.add(pool.submit(task));
        }
        start.countDown();

        int confirmed = 0;
        for (Future<OrderStatus> f : results) {
            if (f.get() == OrderStatus.CONFIRMED) confirmed++;
        }
        pool.shutdown();

        assertThat(confirmed).isEqualTo(5);
        assertThat(stockOf(104L)).isZero();
    }

    @Test
    @DisplayName("Bonus: same requestId twice creates only one order and deducts stock once")
    void idempotentSubmission() throws Exception {
        String body = """
                {"customerId":"CUST001","requestId":"REQ-42","items":[{"productId":101,"quantity":2}]}
                """;
        JsonNode first = objectMapper.readTree(mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(second.get("orderId").asLong()).isEqualTo(first.get("orderId").asLong());
        assertThat(stockOf(101L)).isEqualTo(8);
    }

    @Test
    @DisplayName("Bonus: cancelling a confirmed order restores stock; second cancel is 409")
    void cancelRestoresInventory() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customerId":"CUST001","items":[{"productId":104,"quantity":3}]}
                """)).andExpect(status().isCreated());
        assertThat(stockOf(104L)).isEqualTo(2);

        mockMvc.perform(post("/api/orders/1001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(104L)).isEqualTo(5);

        mockMvc.perform(post("/api/orders/1001/cancel")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/audit/1001"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].action").value("ORDER_CANCELLED"));
    }

    private int stockOf(Long productId) {
        return inventoryRepository.findById(productId).orElseThrow().getAvailableQty();
    }
}
