//package com.nabajyoti.plain;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//class Solution2Test {
//
//    private static final ObjectMapper mapper = new ObjectMapper();
//
//    @Test
//    void getTotalPrice_incrementalTiers_sumsPerItemShipping() throws Exception {
//        Order order = new Order(List.of(
//                new Item("Laptop", 3),
//                new Item("Keyboard", 15)
//        ));
//        String ratesJson = """
//                {
//                  "Laptop": [
//                    {"min_quantity": 1, "max_quantity": 5, "type": "incremental", "unit_shipping_price": 100},
//                    {"min_quantity": 6, "max_quantity": 10, "type": "incremental", "unit_shipping_price": 80},
//                    {"min_quantity": 11, "max_quantity": null, "type": "incremental", "unit_shipping_price": 60}
//                  ],
//                  "Keyboard": [
//                    {"min_quantity": 1, "max_quantity": 10, "type": "incremental", "unit_shipping_price": 20},
//                    {"min_quantity": 11, "max_quantity": null, "type": "incremental", "unit_shipping_price": 15}
//                  ]
//                }
//                """;
//        JsonNode shippingRates = mapper.readTree(ratesJson);
//
//        assertEquals(525L, Solution2.getTotalPrice(shippingRates, order));
//    }
//}
