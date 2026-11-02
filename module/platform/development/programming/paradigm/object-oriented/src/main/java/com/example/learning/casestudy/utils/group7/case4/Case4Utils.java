package com.example.learning.casestudy.utils.group7.case4;

import com.example.learning.casestudy.domain.group7.case4.Child;
import com.example.learning.casestudy.domain.group7.case4.Parent;
import com.example.learning.casestudy.dto.group7.case4.ParentDto;
import java.util.ArrayList;
import java.util.List;

public final class Case4Utils {
    private Case4Utils() {

    }

    public static ParentDto toDto(Parent parent){
        List<String> ids = new ArrayList<>();
        for (Child c:parent.children)ids.add(c.id);
        return new ParentDto(parent.id, List.copyOf(ids));

    }

    public static String toJsonLike(ParentDto dto){
        return "{\"id\":\""+dto.id()+"\",\"childIds\":[\""
        +String.join("\",\"", dto.childIds())+"\"]}";

    }

    public static void check(boolean ok){
        if (!ok)throw new AssertionError();

    }
}
