package dev.evgeni.integrationsandbox.config;

import java.io.File;
import java.util.Date;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import dev.evgeni.integrationsandbox.model.Item;

// @Configuration
public class DbToFileIntegrationConfig {

    private final String SELECT_SQL_QUERY = """
            SELECT * FROM ITEMS WHERE CREATED_AT > current_timestamp - interval '5 seconds';
            """;


    @Bean
    public MessageChannel newItemsInDbChannel() {
        return new DirectChannel();
    }

    // poll DB every 30 seconds for new reqords with some SQL query
    @Bean
    @InboundChannelAdapter(channel = "newItemsInDbChannel", poller = @Poller(fixedDelay = "5000"))
    public MessageSource<?> jdbcPoller(DataSource datasource) {
        JdbcPollingChannelAdapter adapter =
                new JdbcPollingChannelAdapter(datasource, SELECT_SQL_QUERY);
        adapter.setRowMapper((rs, index) -> new Item(rs.getString("ITEM_ID"),
                rs.getString("DESCRIPTION"), rs.getInt("INVENTORY_STATUS"),
                new Date(rs.getTimestamp("CREATED_AT").getTime())));

        return adapter;
    }

    @Transformer(inputChannel = "newItemsInDbChannel",
            outputChannel = "newItemsReadyForFileInsertChannel")
    public String csvRowConverter(List<Item> items) {
        String result = "";
        for (Item item : items) {
            result += item.getItemNo() + "," + item.getDescription() + ","
                    + item.getInventoryStatus() + item.getCreatedAt() + "\n";
        }

        return result;
    }


    @Bean
    @ServiceActivator(inputChannel = "newItemsReadyForFileInsertChannel")
    public MessageHandler fileWriter() {
        FileWritingMessageHandler fileWriter =
                new FileWritingMessageHandler(new File("/tmp/items"));
        fileWriter.setFileExistsMode(FileExistsMode.APPEND);
        fileWriter.setAppendNewLine(false);
        fileWriter.setFileNameGenerator(message -> "items_table.csv");
        fileWriter.setExpectReply(false);

        return fileWriter;
    }
}
