package com.carwash.testsupport;

import com.carwash.api.dto.CreateQueueEntryRequest;

public final class QueueFixtureBuilder {

    private String queueEntryId;
    private String bookingId;
    private String serviceId;
    private Integer position = 1;

    private QueueFixtureBuilder() {
    }

    public static QueueFixtureBuilder valid(TestIdFactory ids, String bookingId, String serviceId) {
        QueueFixtureBuilder builder = new QueueFixtureBuilder();
        builder.queueEntryId = ids.queueEntry();
        builder.bookingId = bookingId;
        builder.serviceId = serviceId;
        return builder;
    }

    public QueueFixtureBuilder queueEntryId(String value) { this.queueEntryId = value; return this; }
    public QueueFixtureBuilder bookingId(String value) { this.bookingId = value; return this; }
    public QueueFixtureBuilder serviceId(String value) { this.serviceId = value; return this; }
    public QueueFixtureBuilder position(Integer value) { this.position = value; return this; }

    public CreateQueueEntryRequest build() {
        return new CreateQueueEntryRequest(queueEntryId, bookingId, serviceId, position);
    }
}
