package com.krishna.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.model.Order;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.annotation.BlobOutput;
import com.microsoft.azure.functions.OutputBinding;

/**
 * Service Bus triggered function.
 *
 * Automatically runs whenever a new message arrives on "orders-queue".
 * Parses the order JSON, marks it as PROCESSED, and saves it as a
 * .json file in Blob Storage (container: "orders").
 *
 * This demonstrates an async processing pipeline:
 *   HTTP request -> Service Bus queue -> Background processing -> Blob Storage
 */
public class OrderProcessorFunction {

    @FunctionName("ProcessOrder")
    @BlobOutput(
        name = "outputBlob",
        path = "orders/{DateTime}-{rand-guid}.json",
        connection = "AzureWebJobsStorage")
    public String run(
            @ServiceBusQueueTrigger(
                name = "message",
                queueName = "orders-queue",
                connection = "ServiceBusConnection")
                String message,
            final ExecutionContext context) throws Exception {

        context.getLogger().info("ProcessOrder triggered. Message: " + message);

        ObjectMapper mapper = new ObjectMapper();
        Order order = mapper.readValue(message, Order.class);

        // Mark as processed
        order.setStatus("PROCESSED");

        context.getLogger().info(
            "Order " + order.getOrderId() +
            " processed for customer " + order.getCustomerName() +
            ". Total: " + order.getTotalAmount()
        );

        // The returned string becomes the content of the blob file
        // (path defined in @BlobOutput above - one JSON file per order)
        return mapper.writeValueAsString(order);
    }
}