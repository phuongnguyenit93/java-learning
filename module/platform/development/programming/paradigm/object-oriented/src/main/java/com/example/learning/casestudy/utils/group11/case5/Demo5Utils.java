package com.example.learning.casestudy.utils.group11.case5;

import com.example.learning.casestudy.domain.group11.case5.LineView;
import com.example.learning.casestudy.domain.group11.case5.OrderSnapshot;
import com.example.learning.casestudy.model.group11.case5.MutableLine;
import java.util.List;

public final class Demo5Utils {
    private Demo5Utils() {
    }

    public static OrderSnapshot fromMutable(List<MutableLine> source) {
        List<LineView> projected = source.stream()
        .map(x -> new LineView(x.sku, x.quantity)).toList();
        return new OrderSnapshot(projected);
    }
}
