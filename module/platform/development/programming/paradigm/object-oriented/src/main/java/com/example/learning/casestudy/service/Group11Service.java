package com.example.learning.casestudy.service;

import com.example.learning.casestudy.domain.group11.case1.Customer1;
import com.example.learning.casestudy.domain.group11.case10.Animal10;
import com.example.learning.casestudy.domain.group11.case10.Cat10;
import com.example.learning.casestudy.domain.group11.case10.Dog10;
import com.example.learning.casestudy.domain.group11.case11.Animal11;
import com.example.learning.casestudy.domain.group11.case11.Cat11;
import com.example.learning.casestudy.domain.group11.case11.Dog11;
import com.example.learning.casestudy.domain.group11.case2.ProductKey;
import com.example.learning.casestudy.domain.group11.case3.BadBase;
import com.example.learning.casestudy.domain.group11.case3.BadSubtype;
import com.example.learning.casestudy.domain.group11.case3.Identity;
import com.example.learning.casestudy.domain.group11.case3.NormalCustomer;
import com.example.learning.casestudy.domain.group11.case3.PremiumCustomer;
import com.example.learning.casestudy.domain.group11.case4.Product;
import com.example.learning.casestudy.domain.group11.case5.LineView;
import com.example.learning.casestudy.domain.group11.case5.OrderSnapshot;
import com.example.learning.casestudy.domain.group11.case6.Node;
import com.example.learning.casestudy.domain.group11.case7.User;
import com.example.learning.casestudy.domain.group11.case8.Address;
import com.example.learning.casestudy.domain.group11.case8.Profile;
import com.example.learning.casestudy.domain.group11.case9.Customer9;
import com.example.learning.casestudy.model.group11.case5.MutableLine;
import com.example.learning.casestudy.port.group11.case9.Repository;
import com.example.learning.casestudy.repository.group11.case9.MemoryRepository;
import com.example.learning.casestudy.utils.group11.case10.Demo10Utils;
import com.example.learning.casestudy.utils.group11.case11.Demo11Utils;
import com.example.learning.casestudy.utils.group11.case5.Demo5Utils;
import com.example.learning.casestudy.utils.group11.case9.Demo9Utils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup11Service")
public class Group11Service {
    public void executeCase1() {
        Customer1 original = new Customer1("C-1", "Before");
        Map<Customer1, String> map = new HashMap<>();
        Set<Customer1> set = new HashSet<>();
        map.put(original, "active");
        set.add(original);
        int hashBefore = original.hashCode();
        original.rename("After");
        assert original.hashCode() == hashBefore;
        assert map.get(original).equals("active");
        assert map.get(new Customer1("C-1", "Different name")).equals("active");
        assert set.contains(new Customer1("C-1", "Alias"));
        assert set.size() == 1;
    }

    public void executeCase2() {
        ProductKey a = new ProductKey("BOOK", "42");
        ProductKey b = new ProductKey("BOOK", "42");
        ProductKey c = new ProductKey("BOOK", "42");
        ProductKey different = new ProductKey("BOOK", "43");
        assert a.equals(a);
        assert a.equals(b) && b.equals(a);
        assert b.equals(c) && a.equals(c);
        assert a.hashCode() == b.hashCode();
        assert !a.equals(different) && !a.equals(null);
        Map<ProductKey, Integer> price = new HashMap<>();
        price.put(a, 250);
        assert price.get(b) == 250;
        assert price.size() == 1;
    }

    public void executeCase3() {
        BadBase base = new BadBase("X");
        BadBase subtype = new BadSubtype("X");
        assert base.equals(subtype);
        // đường sai
        assert !subtype.equals(base);
        // symmetry bị phá

        Identity sharedId = new Identity("X");
        NormalCustomer normal = new NormalCustomer(sharedId, "A");
        PremiumCustomer premium = new PremiumCustomer(sharedId, 2);
        assert normal.identity().equals(premium.identity());
        assert premium.identity().equals(normal.identity());
        assert !normal.equals(premium);
        // không đánh đồng hai loại object
    }

    public void executeCase4() {
        Product a = new Product("Bút", "SKU-1");
        Product b = new Product("Bút", "SKU-2");
        SortedSet<Product> broken =
        new TreeSet<>(Comparator.comparing(Product::name));
        broken.add(a);
        broken.add(b);
        assert broken.size() == 1;
        // lỗi nghiệp vụ: mất SKU thứ hai

        Comparator<Product> safeOrder = Comparator
        .comparing(Product::name)
        .thenComparing(Product::sku);
        SortedSet<Product> fixed = new TreeSet<>(safeOrder);
        fixed.add(a);
        fixed.add(b);
        assert fixed.size() == 2;
        assert fixed.contains(a) && fixed.contains(b);
        assert safeOrder.compare(a, b) != 0;
        assert safeOrder.compare(a, a) == 0;
        assert fixed.first().sku().equals("SKU-1");
    }

    public void executeCase5() {
        MutableLine original = new MutableLine("A", 2);
        List<MutableLine> source = new ArrayList<>(List.of(original));
        OrderSnapshot historical = Demo5Utils.fromMutable(source);
        original.quantity = 99;
        source.add(new MutableLine("B", 1));
        assert historical.lines().equals(List.of(new LineView("A", 2)));
        try {
            historical.lines().add(new LineView("BAD", 1));
            throw new AssertionError("mutable result");
        }
        catch (UnsupportedOperationException expected) {
        }
        List<LineView> sourceViews = new ArrayList<>(List.of(new LineView("X", 3)));
        OrderSnapshot direct = new OrderSnapshot(sourceViews);
        sourceViews.clear();
        assert direct.lines().equals(List.of(new LineView("X", 3)));
    }

    public void executeCase6() {
        Node root = new Node("root"), child = new Node("child");
        root.attach(child);
        // root.children -> child.parent -> root là object cycle
        String a = root.toString();
        String b = child.toString();
        assert a.contains("childCount=1");
        assert b.contains("parentId=root");
        assert a.length() < 100 && b.length() < 100;
        assert !a.contains("Node{id='child'");
    }

    public void executeCase7() {
        String token = "tok-SECRET-123";
        String pan = "4111111111111111";
        User user = new User("U-1", "VIEWER", token, pan);
        String text = user.toString();
        String structured = user.safeLogFields().toString();
        assert text.contains("U-1") && structured.contains("VIEWER");
        assert !text.contains(token) && !text.contains(pan);
        assert !structured.contains(token) && !structured.contains(pan);
        assert !text.contains("accessToken") && !text.contains("cardNumber");
    }

    public void executeCase8() {
        Address external = new Address("Hanoi");
        List<Address> initial = new ArrayList<>(List.of(external));
        Profile original = new Profile(initial);
        Profile copy = new Profile(original);
        copy.relocateFirst("Danang");
        assert original.firstCity().equals("Hanoi");
        assert copy.firstCity().equals("Danang");
        external.city = "Hue";
        initial.clear();
        assert original.firstCity().equals("Hanoi");
        assert copy.firstCity().equals("Danang");
    }

    public void executeCase9() {
        Repository<Customer9, String> customers = new MemoryRepository<>();
        customers.save("C1", new Customer9("C1", "Mai"));
        assert Demo9Utils.customerName(customers, "C1").equals("Mai");
        assert customers.find("missing").isEmpty();
        // customers.save("C2", "not a Customer9"); // lỗi compile-time
        // Integer wrong = customers.find("C1").orElseThrow(); // lỗi compile-time
    }

    public void executeCase10() {
        List<Dog10> dogs = new ArrayList<>(List.of(new Dog10()));
        assert Demo10Utils.sounds(dogs).equals("woof;");
        // List<Animal10> wrong = dogs; // không biên dịch: List<Dog10> != List<Animal10>
        List<Animal10> writable = new ArrayList<>(dogs);
        writable.add(new Cat10());
        assert Demo10Utils.sounds(writable).equals("woof;meow;");
        assert dogs.size() == 1;
        assert dogs.get(0).sound().equals("woof");
    }

    public void executeCase11() {
        List<Dog11> dogs = List.of(new Dog11("Rex"), new Dog11("Luna"));
        List<Animal11> animals = new ArrayList<>();
        List<Object> audit = new ArrayList<>();
        Demo11Utils.copyAnimals(dogs, animals);
        Demo11Utils.copyAnimals(dogs, audit);
        assert animals.size() == 2;
        assert animals.get(0).name().equals("Rex");
        assert audit.size() == 2;
        assert audit.get(1).equals(new Dog11("Luna"));
        animals.add(new Cat11("Mimi"));
        assert dogs.size() == 2;
    }
}
