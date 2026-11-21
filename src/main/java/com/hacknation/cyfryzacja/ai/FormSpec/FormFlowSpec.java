package com.hacknation.cyfryzacja.formspec;

import java.util.List;

public record FormFlowSpec(
        List<FlowLinkSpec> links
) {
}