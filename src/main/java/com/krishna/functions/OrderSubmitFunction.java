package com.krishna.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.model.Order;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * HTTP-triggered function that receives a new order from a client,
 * validates it, assigns an order ID, and sends it to a Service Bus queue
 * for asynchronous processing.
 *
 * Endpoint: POST /api/orders
 */
public class OrderSubmitFunction {

    @FunctionName("SubmitOrder")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "orders")
                HttpRequestMessage<Optional<String>> request,
            @ServiceBusQueueOutput(
                name = "ordersQueue",
                queueName = "orders-queue",
                connection = "ServiceBusConnection")
                OutputBinding<String> ordersQueue,
            final ExecutionContext context) {

        context.getLogger().info("SubmitOrder function triggered.");

        String body = request.getBody().orElse(null);
        if (body == null || body.isBlank()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"Request body is required\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Order order = mapper.readValue(body, Order.class);

            // Basic validation
            if (order.getCustomerName() == null || order.getCustomerName().isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"customerName is required\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"items must not be empty\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }

            // Assign a unique order ID and mark as pending
            order.setOrderId(UUID.randomUUID().toString());
            order.setStatus("PENDING");

            // Calculate total from items (don't trust client-provided total)
            double total = 0;
            for (var item : order.getItems()) {
                total += item.getPrice() * item.getQuantity();
            }
            order.setTotalAmount(total);

            // Send the order as JSON to the Service Bus queue.
            // The OrderProcessorFunction (next function) will pick this up.
            String orderJson = mapper.writeValueAsString(order);
            ordersQueue.setValue(orderJson);

            context.getLogger().info("Order " + order.getOrderId() + " sent to queue.");

            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .body(orderJson)
                    .header("Content-Type", "application/json")
                    .build();

        } catch (Exception e) {
            context.getLogger().severe("Error processing order: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to process order: " + e.getMessage() + "\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
}