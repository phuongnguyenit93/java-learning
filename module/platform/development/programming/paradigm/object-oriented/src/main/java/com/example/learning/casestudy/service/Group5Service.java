package com.example.learning.casestudy.service;

import com.example.learning.casestudy.adapter.group5.case10.PrefixPlugin;
import com.example.learning.casestudy.adapter.group5.case10.UpperPlugin;
import com.example.learning.casestudy.adapter.group5.case12.PlainRenderer;
import com.example.learning.casestudy.adapter.group5.case12.PrefixRenderer;
import com.example.learning.casestudy.adapter.group5.case12.UpperRenderer;
import com.example.learning.casestudy.adapter.group5.case5.DiskReader;
import com.example.learning.casestudy.adapter.group5.case5.ServiceReader;
import com.example.learning.casestudy.application.group5.case10.Engine;
import com.example.learning.casestudy.application.group5.case12.Screen;
import com.example.learning.casestudy.domain.group5.case1.Bicycle;
import com.example.learning.casestudy.domain.group5.case1.Car;
import com.example.learning.casestudy.domain.group5.case11.Rectangle;
import com.example.learning.casestudy.domain.group5.case11.Square;
import com.example.learning.casestudy.domain.group5.case2.SavingsAccount;
import com.example.learning.casestudy.domain.group5.case3.Invoice;
import com.example.learning.casestudy.domain.group5.case4.Promotion;
import com.example.learning.casestudy.domain.group5.case4.Standard;
import com.example.learning.casestudy.domain.group5.case7.Base7;
import com.example.learning.casestudy.domain.group5.case7.Child7;
import com.example.learning.casestudy.domain.group5.case8.Base8;
import com.example.learning.casestudy.domain.group5.case8.Child8;
import com.example.learning.casestudy.domain.group5.case9.Normalizer;
import com.example.learning.casestudy.domain.group5.case9.TrimNormalizer;
import com.example.learning.casestudy.model.group5.case8.OldBase;
import com.example.learning.casestudy.model.group5.case8.OldChild;
import com.example.learning.casestudy.port.group5.case1.EnginePowered;
import com.example.learning.casestudy.port.group5.case1.Vehicle;
import com.example.learning.casestudy.port.group5.case12.Renderer;
import com.example.learning.casestudy.port.group5.case5.Reader;
import com.example.learning.casestudy.utils.group5.case1.Case1Utils;
import com.example.learning.casestudy.utils.group5.case11.Case11Utils;
import com.example.learning.casestudy.utils.group5.case12.Case12Utils;
import com.example.learning.casestudy.utils.group5.case2.Case2Utils;
import com.example.learning.casestudy.utils.group5.case3.Invoice3Utils;
import com.example.learning.casestudy.utils.group5.case4.Case4Utils;
import com.example.learning.casestudy.utils.group5.case5.Case5Utils;
import com.example.learning.casestudy.utils.group5.case6.Case6Utils;
import com.example.learning.casestudy.utils.group5.case7.Base7Utils;
import com.example.learning.casestudy.utils.group5.case7.Child7Utils;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup5Service")
public class Group5Service {
    public void executeCase1() {
        Vehicle bicycle = new Bicycle();
        Vehicle car = new Car();
        assert bicycle.move().equals("pedal");
        assert car.move().equals("drive");
        assert Case1Utils.beginTrip(new Car()).equals("engine started");
        // Case1Utils.beginTrip(new Bicycle()); // compile error: Bicycle không có capability EnginePowered
    }

    public void executeCase2() {
        Case2Utils.contract(new SavingsAccount(100));
        assert new SavingsAccount(0).balance() == 0;
    }

    public void executeCase3() {
        Invoice invoice = Invoice3Utils.create("I1", List.of("item"));
        assert invoice.rowCount() == 1;
        try {
            Invoice3Utils.create("I2", List.of());
            throw new AssertionError("expected validation failure");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void executeCase4() {
        Case4Utils.contract(new Standard(100, 8), 100);
        Case4Utils.contract(new Promotion(100, 8), 90);
        assert new Promotion(100, 8).total() == 98;
    }

    public void executeCase5() {
        try {
            Reader disk = new DiskReader(), service = new ServiceReader();
            assert disk.read().equals("found");
            assert service.read().equals("ready");
        } catch (IOException checked) {
            throw Case5Utils.<RuntimeException>rethrow(checked);
        }
    }

    public void executeCase6() {
        assert Case6Utils.choose(null).equals("string");
        // String đặc hiệu hơn Object
        assert Case6Utils.choose((Object) null).equals("object");
        assert Case6Utils.choose((String) null).equals("string");
        assert Case6Utils.fromString(null).equals("string-api");
        assert Case6Utils.fromInteger(null).equals("integer-api");
    }

    public void executeCase7() {
        Base7 ref = new Child7();
        assert Base7Utils.kind().equals("base-static");
        assert Child7Utils.kind().equals("child-static");
        assert Base7Utils.kind().equals("base-static");
        // hợp lệ nhưng nên tránh gọi static qua instance
        assert ref.render().equals("child-instance");
    }

    public void executeCase8() {
        OldChild oldChild = new OldChild();
        OldBase oldRef = oldChild;
        assert oldRef.code.equals("BASE");
        assert oldChild.code.equals("CHILD");
        // bug: hai field cùng tên
        Child8 child = new Child8();
        Base8 ref = child;
        child.assign("ONE");
        assert ref.code().equals("ONE");
        assert child.code().equals("ONE");
    }

    public void executeCase9() {
        Normalizer a = new Normalizer();
        Normalizer b = new TrimNormalizer();
        assert a.process(" x ").equals("[ x ]");
        assert b.process(" x ").equals("[x]");
    }

    public void executeCase10() {
        assert new Engine(new UpperPlugin()).process("hello").equals("HELLO");
        assert new Engine(new PrefixPlugin()).process("hello").equals("PLUGIN:hello");
        // class Derived extends Engine {} // không biên dịch: Engine là final
    }

    public void executeCase11() {
        Rectangle rectangle = new Rectangle(2, 5);
        Square square = new Square(4);
        assert rectangle.area() == 10;
        assert square.area() == 16;
        assert Case11Utils.sumAreas(rectangle, square) == 26;
        assert rectangle.width() == 2 && rectangle.height() == 5;
        assert square.side() == 4;
    }

    public void executeCase12() {
        Renderer plain = new PlainRenderer();
        Renderer themed = new PrefixRenderer(plain, "Theme:");
        Renderer upper = new UpperRenderer(themed);
        Case12Utils.contract(plain);
        Case12Utils.contract(themed);
        Case12Utils.contract(upper);
        assert new Screen(plain).show("hello").equals("hello");
        assert new Screen(themed).show("hello").equals("Theme:hello");
        assert new Screen(upper).show("hello").equals("THEME:HELLO");
    }
}
