package com.hacknation.cyfryzacja.ai.FormSpec;

import java.util.List;

public record FormFlowSpec(
        List<FlowLinkSpec> links
) {
}