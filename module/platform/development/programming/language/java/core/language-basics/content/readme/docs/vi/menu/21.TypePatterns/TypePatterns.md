<a id="back-to-top"></a>

# Mẫu kiểm tra kiểu

## Menu
- [instanceof kiểm tra kiểu và kết quả khi đối tượng là null](#type-patterns-001)
- [Type pattern kết hợp kiểm tra kiểu và khai báo biến](#type-patterns-002)
- [Phạm vi biến mẫu phụ thuộc luồng điều kiện](#type-patterns-003)
- [Kết hợp instanceof pattern với &amp;&amp; hoặc guard clause](#type-patterns-004)
- [Pattern matching cho switch trong Java 21](#type-patterns-005)
- [Điều kiện bảo vệ when và thứ tự nhãn mẫu](#type-patterns-006)
- [Mẫu bao phủ, nhãn không thể được chọn và yêu cầu đầy đủ](#type-patterns-007)
- [Nhận diện record pattern để tách dữ liệu của record](#type-patterns-008)
- [Phân biệt tính năng chính thức với cú pháp preview theo phiên bản](#type-patterns-009)

## <a id="type-patterns-001">instanceof kiểm tra kiểu và kết quả khi đối tượng là null</a>

<details>
<summary>Xem chi tiết</summary>

# `instanceof` kiểm tra kiểu và kết quả khi đối tượng là `null` trong Java

> **Java core — language-basics / Mẫu kiểm tra kiểu**  
> **Môi trường:** Java 17+, không bật preview, chương trình một file và chỉ dùng JDK.  
> **Mục tiêu:** Hiểu `instanceof` kiểm tra kiểu đối tượng khi chạy, biết `null instanceof T` luôn là `false`, dùng pattern matching `instanceof` để xử lý an toàn, và tránh lỗi ép kiểu, phạm vi biến mẫu cùng hạn chế generic.

## 1. `instanceof` kiểm tra điều gì?

Toán tử `instanceof` trả về **`boolean`**, kiểm tra một tham chiếu có trỏ đến đối tượng thuộc kiểu tham chiếu cần kiểm tra hay không. Cú pháp cơ bản:

~~~java
Object value = "Java";

boolean isText = value instanceof String;       // true
boolean isSequence = value instanceof CharSequence; // true
boolean isNumber = value instanceof Number;    // false
~~~

`value` được khai báo kiểu `Object` nhưng đối tượng **thực tế lúc chạy** là `String`. Vì `String` triển khai `CharSequence`, cả hai phép kiểm tra đầu đều true.

`instanceof` không kiểm tra nội dung hoặc tính bằng nhau của hai đối tượng; đây là phép **kiểm tra quan hệ kiểu runtime**. Đừng nhầm nó với `==`, `equals()` hoặc `getClass() == SomeType.class`.

| Phép kiểm tra | Câu hỏi |
| --- | --- |
| `x instanceof T` | `x` khác null và đối tượng runtime tương thích với `T`? |
| `x == null` | Tham chiếu có đang null không? |
| `x.getClass() == T.class` | Lớp runtime có **chính xác** là `T`, không tính lớp con? |
| `x.equals(y)` | Hai đối tượng có bằng nhau theo ngữ nghĩa `equals` của lớp không? |

`getClass()` sẽ ném `NullPointerException` khi `x == null`; trái lại, `instanceof` **an toàn với null**.

## 2. `null instanceof T` luôn trả `false`

`null` nghĩa là **không có đối tượng nào**. Vì vậy tham chiếu null không phải là instance của bất kỳ kiểu lớp, interface hoặc kiểu mảng nào.

~~~java
Object value = null;

System.out.println(value instanceof Object); // false
System.out.println(value instanceof String); // false
System.out.println(value instanceof Runnable); // false
System.out.println(value instanceof int[]); // false
~~~

Kết quả này quan trọng ngay cả với `Object`: `null instanceof Object` cũng là false. Toán tử **không ném `NullPointerException`**, và phép kiểm tra trả true đồng thời xác nhận tham chiếu khác null.

Ví dụ:

~~~java
Object maybeText = null;

if (maybeText instanceof String) {
    System.out.println("String");
} else {
    System.out.println("Null or another type");
}
// In: Null or another type.
~~~

**Nhánh `else` không chứng minh đối tượng là null**: nó cũng nhận mọi đối tượng không phải `String`. Nếu cần phân biệt ba trạng thái, hãy kiểm tra null riêng:

~~~java
if (maybeText == null) {
    System.out.println("MISSING");
} else if (maybeText instanceof String) {
    System.out.println("TEXT");
} else {
    System.out.println("OTHER_TYPE");
}
~~~

## 3. Lớp con, interface và kiểu mảng

`instanceof` xem xét quan hệ kế thừa và triển khai interface, không chỉ lớp chính xác:

~~~java
class Animal {}
interface Named {}
class Dog extends Animal implements Named {}

Animal pet = new Dog();

boolean isAnimal = pet instanceof Animal; // true
boolean isDog = pet instanceof Dog;       // true
boolean isNamed = pet instanceof Named;   // true
~~~

Đối tượng runtime là `Dog` nên nó cũng là `Animal` và `Named`. Nếu tham chiếu `Animal` trỏ tới một `Cat` không triển khai `Named`, phép kiểm tra `Dog` hoặc `Named` sẽ false.

Mảng cũng là đối tượng:

~~~java
Object data = new int[] {1, 2};
boolean primitiveArray = data instanceof int[];   // true
boolean object = data instanceof Object;          // true
boolean referenceArray = data instanceof Object[]; // false
~~~

`int[]` không phải `Object[]`: mảng primitive chứa các giá trị `int`, không phải tham chiếu đối tượng. Với `String[]`, phép kiểm tra `instanceof Object[]` lại true vì các phần tử là tham chiếu.

## 4. Kiểm tra kiểu trước khi ép kiểu

Ép kiểu tham chiếu **không kiểm tra an toàn giúp bạn trước khi cast**; nếu đối tượng runtime không tương thích, Java ném `ClassCastException`:

~~~java
Object value = Integer.valueOf(7);

// String text = (String) value;
// Nếu chạy: ClassCastException.
~~~

Mẫu cũ thường viết:

~~~java
Object value = "Java";

if (value instanceof String) {
    String text = (String) value;
    System.out.println(text.length()); // 4
}
~~~

`instanceof String` là **điều kiện bảo vệ** trước phép cast. Nếu `value` null thì nhánh không chạy; nếu giá trị không phải String thì cũng không cast.

Một chi tiết dễ nhầm: **ép kiểu tham chiếu null có thể hợp lệ**:

~~~java
Object nothing = null;
String text = (String) nothing; // text vẫn null; cast không ném CCE.
// text.length(); // Nếu gọi: NullPointerException.
~~~

Nói cách khác, `instanceof` kiểm tra có đối tượng tương thích hay không; cast null không tạo ra đối tượng mới và không tự gọi phương thức.

## 5. Java 17: pattern matching với `instanceof`

Java 17 hỗ trợ cú pháp **type pattern** ngay trong phép kiểm tra:

~~~java
Object value = "hello";

if (value instanceof String text) {
    System.out.println(text.toUpperCase()); // HELLO
}
~~~

Ở đây `text` là **biến mẫu** (*pattern variable*): khi kiểm tra true, Java tự thực hiện bước ràng buộc tham chiếu theo kiểu `String`. Nhờ đó không phải cast tường minh một lần nữa.

Biến mẫu chỉ được sử dụng nơi Java xác định rằng phép kiểm tra kiểu đã thành công (**flow scoping**):

~~~java
Object value = " Java ";

if (value instanceof String text && !text.isBlank()) {
    System.out.println(text.trim()); // Java
}
~~~

Với `&&`, vế phải chỉ được đánh giá khi kiểm tra kiểu true, nên `text` hợp lệ và không null ở vế phải. Trái lại:

~~~java
// KHÔNG BIÊN DỊCH: text không được đảm bảo đã có giá trị ở vế phải.
// if (value instanceof String text || text.isBlank()) { }
~~~

Trong `||`, vế phải chỉ chạy khi vế trái false, tức chưa thể gán biến mẫu `text`. Không dùng biến mẫu ngoài phạm vi mà trình biên dịch xác nhận.

## 6. Biến mẫu sau `return` và những giới hạn của kiểu

Một cách viết Java 17 rõ ràng là thoát sớm khi không khớp:

~~~java
static String uppercaseIfString(Object input) {
    if (!(input instanceof String text)) {
        return "NOT_TEXT";
    }
    return text.toUpperCase(); // text được xác định đã khớp.
}
~~~

Nếu điều kiện phủ định đúng, phương thức đã `return`. Khi luồng tới dòng cuối, Java biết `input instanceof String text` là true nên `text` có sẵn và khác null.

**Các giới hạn cần nhớ:**

- `instanceof` dùng cho **tham chiếu** ở Java 17; không thể viết `int x = 3; x instanceof Integer`. Muốn đưa số nguyên vào `Object`, autoboxing có thể tạo `Integer`.
- Kiểu bên phải phải phù hợp với ràng buộc kiểm tra runtime. Không thể thử trực tiếp `instanceof List<String>` vì thông tin tham số generic bị xóa (`type erasure`); có thể dùng `List<?>`.
- Với **type pattern**, Java 17 từ chối một số trường hợp mà kiểu tĩnh của biểu thức đã là subtype của kiểu mẫu (kiểm tra vô điều kiện). Ví dụ `String s = "a"; if (s instanceof String t)` là lỗi biên dịch; nếu đã biết kiểu `String`, không cần pattern như vậy.
- Pattern matching `instanceof` trong Java 17 chỉ giới thiệu type pattern; không phải cú pháp record pattern của các phiên bản Java mới.

Ví dụ với generic:

~~~java
Object value = java.util.List.of("A", "B");

if (value instanceof java.util.List<?> list) {
    System.out.println(list.size()); // 2
}

// KHÔNG BIÊN DỊCH: type argument String không kiểm tra được tại runtime.
// if (value instanceof java.util.List<String> list) { }
~~~

Kiểm tra `List<?>` không chứng minh các phần tử đều là String. Nếu nghiệp vụ cần kiểm tra loại phần tử, phải xem xét phần tử riêng, không dựa vào `instanceof List<String>`.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **InstanceofNullDemo.java**:

~~~java
import java.util.List;

public final class InstanceofNullDemo {
    interface Named {}

    static class Animal {}
    static final class Dog extends Animal implements Named {}
    static final class Cat extends Animal {}

    private static String describe(Object input) {
        if (input instanceof String text) {
            return "text:" + text.length();
        }
        if (input instanceof Dog dog) {
            return "dog:" + dog.getClass().getSimpleName();
        }
        return "other-or-null";
    }

    private static String safeUpper(Object input) {
        if (!(input instanceof String text)) {
            return "NOT_TEXT";
        }
        return text.toUpperCase();
    }

    public static void main(String[] args) {
        Object absent = null;
        System.out.println("nullObject=" + (absent instanceof Object));
        System.out.println("nullString=" + (absent instanceof String));
        System.out.println("nullNamed=" + (absent instanceof Named));
        System.out.println("nullArray=" + (absent instanceof int[]));

        Object word = "Java";
        System.out.println("wordString=" + (word instanceof String));
        System.out.println("wordCharSequence="
                + (word instanceof CharSequence));
        System.out.println("wordNumber=" + (word instanceof Number));

        Animal pet = new Dog();
        System.out.println("dogAnimal=" + (pet instanceof Animal));
        System.out.println("dogDog=" + (pet instanceof Dog));
        System.out.println("dogNamed=" + (pet instanceof Named));

        Animal cat = new Cat();
        System.out.println("catDog=" + (cat instanceof Dog));
        System.out.println("catNamed=" + (cat instanceof Named));

        Object primitiveArray = new int[] {1, 2};
        Object referenceArray = new String[] {"A", "B"};
        System.out.println("intArray=" + (primitiveArray instanceof int[]));
        System.out.println("intObjectArray="
                + (primitiveArray instanceof Object[]));
        System.out.println("strObjectArray="
                + (referenceArray instanceof Object[]));

        Object boxedNumber = Integer.valueOf(7);
        System.out.println("boxedInteger="
                + (boxedNumber instanceof Integer));

        Object list = List.of("A", "B");
        System.out.println("listWildcard=" + (list instanceof List<?>));

        System.out.println("describeNull=" + describe(null));
        System.out.println("describeText=" + describe("hello"));
        System.out.println("describeDog=" + describe(new Dog()));
        System.out.println("upperNull=" + safeUpper(null));
        System.out.println("upperText=" + safeUpper("java"));

        Object nothing = null;
        String castNull = (String) nothing;
        System.out.println("castNull=" + (castNull == null));

        try {
            String wrong = (String) boxedNumber;
            System.out.println("castValue=" + wrong);
        } catch (ClassCastException ex) {
            System.out.println("badCast=ClassCastException");
        }

        if (word instanceof String text && !text.isBlank()) {
            System.out.println("patternValue=" + text.toUpperCase());
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 InstanceofNullDemo.java
java InstanceofNullDemo
~~~

**Expected stdout chính xác:**

~~~text
nullObject=false
nullString=false
nullNamed=false
nullArray=false
wordString=true
wordCharSequence=true
wordNumber=false
dogAnimal=true
dogDog=true
dogNamed=true
catDog=false
catNamed=false
intArray=true
intObjectArray=false
strObjectArray=true
boxedInteger=true
listWildcard=true
describeNull=other-or-null
describeText=text:5
describeDog=dog:Dog
upperNull=NOT_TEXT
upperText=JAVA
castNull=true
badCast=ClassCastException
patternValue=JAVA
~~~

Đặc biệt, **bốn dòng `null...=false`** cho thấy `instanceof` không ném NPE khi tham chiếu là null. `intObjectArray=false` là do `int[]` không phải `Object[]`; `strObjectArray=true` do `String[]` là mảng tham chiếu. Biến mẫu `text` chỉ được sử dụng khi đã khớp kiểu.

## 8. Sai lầm phổ biến và bài tập

| Tình huống | Kết quả hoặc cách xử lý đúng |
| --- | --- |
| `null instanceof Object` | **false**, không ném `NullPointerException` |
| `x instanceof String` false | `x` có thể null **hoặc** thuộc kiểu khác |
| Cast đối tượng sai kiểu | Có thể ném `ClassCastException` |
| Cast `null` sang `String` | Hợp lệ, kết quả vẫn null |
| `x instanceof String s && !s.isBlank()` | Hợp lệ: `s` có mặt khi vế phải chạy |
| `x instanceof String s \|\| s.isBlank()` | Không hợp lệ về phạm vi biến mẫu |
| `x instanceof List<String>` | Không hợp lệ do type erasure; dùng `List<?>` khi cần kiểm tra raw shape |

**Bài 1.** Cho `Object x = null;`, dự đoán `x instanceof Object`, `x instanceof String` và `x instanceof Runnable`. Khi nào nên thêm `x == null`?

**Bài 2.** Cho `Object x = "Java";`, kiểm tra `String`, `CharSequence`, `Number`. Giải thích bằng quan hệ kiểu.

**Bài 3.** Viết `if (x instanceof String text)` để chỉ gọi `text.length()` khi an toàn. Thử truyền null và một `Integer`.

**Bài 4.** Giải thích vì sao `List<?>` dùng được trong `instanceof` nhưng `List<String>` không; điều kiện đó có chứng minh mọi phần tử là String không?

**Bài 5.** Viết phương thức nhận `Object` và trả một trong ba nhãn `"MISSING"`, `"TEXT"`, `"OTHER"` mà không cast sai kiểu và không làm mất sự phân biệt null.

### Ghi nhớ

**`instanceof` trả true chỉ khi tham chiếu khác null và đối tượng runtime tương thích với kiểu đích.** Vì vậy `null instanceof T` luôn false. Với Java 17, dùng `instanceof T name` để kiểm tra kiểu và lấy biến mẫu an toàn; chú ý phạm vi biến theo luồng, giới hạn generic, và đừng nhầm `instanceof` với ép kiểu hoặc kiểm tra null riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-002">Type pattern kết hợp kiểm tra kiểu và khai báo biến</a>

<details>
<summary>Xem chi tiết</summary>

# Type pattern kết hợp kiểm tra kiểu và khai báo biến — Java 17+

**Type pattern** của `instanceof` cho phép Java vừa kiểm tra kiểu runtime, vừa khai báo **biến có kiểu tĩnh phù hợp** khi phép kiểm tra thành công. Đây là tính năng chuẩn từ Java 16, dùng được trên Java 17 **không cần bật preview**.

~~~java
Object value = "Hello";

if (value instanceof String text) {
    System.out.println(text.length()); // 5
}
~~~

Cú pháp `expression instanceof Type variable` thực hiện ba việc: kiểm tra đối tượng có tương thích với `Type`; nếu đúng, ràng buộc đối tượng với biến `variable`; cho phép dùng biến trong **phạm vi mà compiler chắc chắn pattern đã khớp** (*flow scoping*).

## 1. Khác gì với `instanceof` truyền thống?

Cách cũ phải ép kiểu rõ ràng sau khi kiểm tra:

~~~java
Object obj = "Java";
if (obj instanceof String) {
    String text = (String) obj;
    System.out.println(text.toUpperCase());
}
~~~

Cách dùng type pattern:

~~~java
Object obj = "Java";
if (obj instanceof String text) {
    System.out.println(text.toUpperCase());
}
~~~

Cả hai đều in `JAVA`. Type pattern bỏ phép cast lặp lại nhưng **không tạo đối tượng mới**, không biến `obj` thành một biến khai báo `String`, và không thay đổi giá trị đối tượng.

| Khía cạnh | Kiểm tra cũ | Type pattern |
| --- | --- | --- |
| Kiểm tra kiểu | `obj instanceof String` | `obj instanceof String text` |
| Dùng như `String` | `(String) obj` | Biến `text` đã có kiểu `String` |
| Đối tượng mới? | Không | Không |
| `obj == null` | Kết quả false | Kết quả false |
| Tính an toàn | Phải cast đúng | Compiler xác định phạm vi biến mẫu |

## 2. Chương trình Java 17+ chạy độc lập

Tạo tệp UTF-8 tên **`TypePatternDemo.java`**:

~~~java
import java.util.List;
import java.util.Locale;

public class TypePatternDemo {
    static void describe(Object value) {
        if (value instanceof String text) {
            System.out.println("string=" + text.toUpperCase(Locale.ROOT)
                    + ",length=" + text.length());
        } else if (value instanceof Integer number) {
            System.out.println("integerTwice=" + (number * 2));
        } else if (value == null) {
            System.out.println("null");
        } else {
            System.out.println("other="
                    + value.getClass().getSimpleName());
        }
    }

    static String normalize(Object input) {
        if (!(input instanceof String text) || text.isBlank()) {
            return "invalid";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    static String classify(Object input) {
        if (input instanceof String text && text.length() >= 3) {
            return "long-enough:" + text;
        }
        return "other";
    }

    static int lengthOf(Object value) {
        if (value instanceof CharSequence chars) {
            return chars.length();
        }
        return -1;
    }

    public static void main(String[] args) {
        System.out.println("== old versus pattern ==");
        Object first = "Hello";
        if (first instanceof String) {
            String oldText = (String) first;
            System.out.println("old=" + oldText.substring(0, 2));
        }
        if (first instanceof String text) {
            System.out.println("pattern=" + text.substring(0, 2));
        }

        System.out.println("== matching types ==");
        describe("Java");
        describe(21);
        describe(3.5);
        describe(null);

        System.out.println("== guards and flow scope ==");
        System.out.println("normalize1=" + normalize("  JAVA  "));
        System.out.println("normalize2=" + normalize("   "));
        System.out.println("normalize3=" + normalize(12));
        System.out.println("normalize4=" + normalize(null));
        System.out.println("classifyCat=" + classify("cat"));
        System.out.println("classifyHi=" + classify("hi"));
        System.out.println("classifyNull=" + classify(null));

        System.out.println("== null and interfaces ==");
        Object missing = null;
        System.out.println("nullMatches="
                + (missing instanceof String candidate));
        System.out.println("lengthString=" + lengthOf("abc"));
        System.out.println("lengthNumber=" + lengthOf(42));
        Object integer = 7;
        if (integer instanceof Number number) {
            System.out.println("numberValue=" + number.intValue());
        }

        System.out.println("== generic wildcard ==");
        Object unknown = List.of("A", "B");
        if (unknown instanceof List<?> items) {
            System.out.println("listSize=" + items.size());
            System.out.println("firstItem=" + items.get(0));
        }

        System.out.println("== branch scope ==");
        Object item = "ready";
        if (item instanceof String text) {
            System.out.println("then=" + text);
        } else {
            System.out.println("else=not-a-string");
        }
        System.out.println("done");
    }
}
~~~

Chạy với **JDK 17 trở lên** trong PowerShell hoặc Bash tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 TypePatternDemo.java
java TypePatternDemo
~~~

**Expected stdout chính xác** của riêng lệnh `java TypePatternDemo`:

~~~text
== old versus pattern ==
old=He
pattern=He
== matching types ==
string=JAVA,length=4
integerTwice=42
other=Double
null
== guards and flow scope ==
normalize1=java
normalize2=invalid
normalize3=invalid
normalize4=invalid
classifyCat=long-enough:cat
classifyHi=other
classifyNull=other
== null and interfaces ==
nullMatches=false
lengthString=3
lengthNumber=-1
numberValue=7
== generic wildcard ==
listSize=2
firstItem=A
== branch scope ==
then=ready
done
~~~

Các lệnh `java -version` và `javac -version` hiển thị thông tin phiên bản phụ thuộc môi trường, không thuộc phần stdout cố định.

## 3. Flow scoping qua ví dụ ngắn

Biến mẫu chỉ dùng được ở những nhánh mà trình biên dịch chứng minh kiểm tra kiểu đã thành công. Với `&&`, vế phải chỉ chạy khi `input instanceof String text` đúng; với `||` đặt ngay sau phép kiểm tra dương, vế phải lại chạy khi mẫu không khớp nên không được dùng `text` ở đó.

~~~java
static String normalize(Object input) {
    if (!(input instanceof String text) || text.isBlank()) {
        return "INVALID";
    }
    return text.strip();
}
~~~

Ở guard phủ định, nếu Java phải tính `text.isBlank()`, điều kiện `!(...)` đã false nên `text` được ràng buộc. Sau guard `return`, mọi đường đi còn lại cũng chắc chắn khớp String. Trái lại, `text` không dùng được ở nhánh `else` của phép kiểm tra dương. Bài về phạm vi biến mẫu sẽ phân tích đầy đủ `if/else`, `!`, `&&`, `||` và toán tử ba ngôi.

## 4. `null`, kiểu cha và interface

~~~java
Object value = null;
boolean matched = value instanceof String text; // false.
~~~

`instanceof` type pattern với `null` **trả false**, không tự ném `NullPointerException`. Trái lại, nếu sau đó gọi phương thức trên `value` mà chưa kiểm tra, vẫn có thể gặp lỗi null.

Pattern kiểm tra **kiểu đối tượng tại runtime** theo quan hệ lớp kế thừa/interface:

~~~java
Object value = "abc";
if (value instanceof CharSequence chars) {
    System.out.println(chars.length()); // 3.
}
~~~

`String` triển khai `CharSequence` nên pattern khớp. Biến `chars` có kiểu tĩnh `CharSequence`; chỉ dùng trực tiếp các phương thức có trong kiểu đó, không tự có toàn bộ phương thức chuyên biệt của `String`.

Trong chuỗi `if/else if`, pattern kiểu cha được đặt trước có thể bắt cả đối tượng kiểu con. Chọn thứ tự theo nghiệp vụ, tránh nhánh con không bao giờ đạt tới do kiểu cha đã xử lý trước.

## 5. Giới hạn và lỗi biên dịch thường gặp

| Tình huống | Đúng hay sai? |
| --- | --- |
| `Object o; o instanceof String s` | Hợp lệ khi `o` đã được khởi tạo |
| `o instanceof String s && s.length() > 0` | Hợp lệ theo flow scoping |
| `o instanceof String s || s.length() > 0` | **Sai**: s có thể chưa ràng buộc ở vế phải |
| `String s = "x"; s instanceof String t` | **Sai khi biên dịch**: kiểu tĩnh luôn là subtype của kiểu pattern |
| `o instanceof List<?> items` | Hợp lệ với kiểu wildcard có thể kiểm tra |
| `o instanceof List<String> items` | Không hợp lệ với `Object o`: type erasure |
| `null instanceof String s` | Không dùng literal `null` làm biểu thức mẫu trực tiếp; dùng biến tham chiếu có giá trị null như ví dụ trên |

**Generic:** Do *type erasure*, Java không thể xác thực đối tượng là `List<String>` chỉ từ thông tin runtime `List`. Pattern `List<?> items` là hợp lệ, nhưng `items.get(0)` trả `Object` theo kiểu tĩnh; muốn coi phần tử là String thì cần kiểm tra từng phần tử riêng.

**Kiểu nguyên thủy:** `instanceof` type pattern dùng cho **kiểu tham chiếu**, không viết `instanceof int x`. Một giá trị `int` được gán vào `Object` sẽ được boxing thành `Integer`, và có thể kiểm tra bằng `instanceof Integer number`.

**Pattern quá hiển nhiên:** Nếu biểu thức trái có kiểu tĩnh `String` và kiểm tra `instanceof String text`, Java 17 từ chối vì phép kiểm tra luôn đúng theo quan hệ subtype; hãy sử dụng chính biến đã có kiểu String.

## 6. Java 17 so với các loại pattern mới hơn

`instanceof Type variable` là tính năng **chuẩn** của Java 17. Nhưng `case String s -> ...` trong **pattern matching for switch** chưa là tính năng chuẩn ở Java 17 (chỉ có preview với điều kiện bật riêng); không tự suy ra có thể dùng type pattern `case` trong switch Java 17 thông thường.

**Record patterns** cũng chưa được chuẩn hóa ở Java 17. Đây là các tính năng khác với kiểm tra kiểu và ràng buộc một biến qua `instanceof`.

## 7. Bài tập tự kiểm tra

1. `Object x = "abc"; if (x instanceof String s) ...` thì `s.length()` bằng mấy?  
   *Đáp án:* 3.
2. `Object x = null; x instanceof String s` cho gì?  
   *Đáp án:* false.
3. Vì sao `x instanceof String s && s.isBlank()` có thể biên dịch?  
   *Đáp án:* `&&` chỉ đánh giá vế phải khi pattern khớp.
4. Vì sao thay `&&` bằng `||` ở ví dụ trên thường sai?  
   *Đáp án:* Vế phải của `||` có thể chạy khi pattern không khớp và s chưa được ràng buộc.
5. Sau `if (!(x instanceof String s)) return;`, có thể dùng `s` ở lệnh tiếp không?  
   *Đáp án:* Có, vì trường hợp không khớp đã thoát phương thức.
6. `Object x = List.of("a"); x instanceof List<String> items` có hợp lệ không?  
   *Đáp án:* Không; thay bằng `List<?>` và kiểm tra phần tử nếu cần.
7. Có thể dùng `instanceof Integer n` cho giá trị chứa trong `Object` không?  
   *Đáp án:* Có nếu thực tế đối tượng là `Integer`.

## Checklist

- [ ] `instanceof Type variable` vừa kiểm tra kiểu vừa khai báo biến mẫu có kiểu tĩnh phù hợp.
- [ ] Biến mẫu chỉ được dùng theo **flow scoping**, không tự tồn tại trong nhánh thất bại.
- [ ] Nắm cách dùng `&&` và guard với `!` để bảo đảm đã khớp.
- [ ] `null` không khớp type pattern.
- [ ] Hiểu quan hệ kế thừa và interface khi chọn kiểu pattern.
- [ ] Không dùng generic cụ thể như `List<String>` trong `instanceof` theo cách không thể kiểm tra runtime.
- [ ] Không nhầm type pattern `instanceof` với pattern-switch/record pattern của Java mới.

**Kết luận:** Type pattern giúp mã Java 17 giảm cast thủ công, nhưng điều quan trọng nhất là **trạng thái kiểm tra kiểu và phạm vi biến đi theo luồng điều khiển**. Dùng `instanceof` với `&&`, kiểm tra null và guard clause đúng cách để viết mã rõ ràng, an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-003">Phạm vi biến mẫu phụ thuộc luồng điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi biến mẫu phụ thuộc luồng điều kiện — Java 17+

## 1. Phạm vi biến mẫu là gì?

Java 17 hỗ trợ **pattern matching cho `instanceof`** (tính năng ổn định từ Java 16). Cú pháp kiểm tra kiểu và khai báo biến mẫu (*pattern variable*) trong cùng một biểu thức:

~~~java
Object value = "Java 17";

if (value instanceof String text) {
    System.out.println(text.length());
}
~~~

**Exact stdout:**

~~~text
7
~~~

Khi điều kiện `value instanceof String text` đúng, Java đã xác nhận `value` là `String` và gán tham chiếu tương ứng cho biến mẫu `text`, không cần cast thủ công.

Điểm quan trọng của bài: **phạm vi của `text` không chỉ phụ thuộc dấu `{ }` mà còn phụ thuộc luồng điều kiện**. Compiler chỉ cho phép sử dụng biến mẫu ở nơi nó chứng minh được rằng mẫu đã **khớp chắc chắn** (*definitely matched*). Cơ chế đó gọi là **flow scoping**.

## 2. Cách đọc luồng thay vì chỉ nhìn dấu ngoặc

Type pattern tránh phải viết lại phép cast sau `instanceof`; điểm cần phân tích ở đây là **nhánh nào bảo đảm mẫu đã khớp**. Chẳng hạn, trong `if (value instanceof String text)`, thân `if` có thể dùng `text` vì điều kiện đúng, còn `else` không có sự bảo đảm đó. Khi điều kiện không khớp mà luồng vẫn chạy tiếp, không thể dùng `text` sau toàn bộ `if`.

Những phần tiếp theo xem xét riêng phép `&&`, `||`, phủ định, guard thoát sớm, toán tử ba ngôi và `else if` để rút ra quy tắc này từ luồng điều khiển. Với `value == null`, kiểm tra kiểu thất bại mà không ném NPE.

## 3. Trong nhánh `if` đúng, biến mẫu dùng được

~~~java
static String inspect(Object value) {
    if (value instanceof String text) {
        return "LEN:" + text.length();
    }
    return "NOT_STRING";
}
~~~

- Khi điều kiện đúng, `text` chắc chắn là một `String`.
- Khi điều kiện sai, không thể dùng `text` ở nhánh `else` của chính phép kiểm tra dương.
- Sau toàn bộ `if`, nếu vẫn có đường đi mà pattern không khớp, `text` cũng **không được coi là chắc chắn tồn tại**.

~~~java
if (value instanceof String text) {
    System.out.println(text.length()); // Hợp lệ.
} else {
    System.out.println("not a String");
    // System.out.println(text); // LỖI COMPILE: text không có phạm vi ở đây.
}
// System.out.println(text); // LỖI nếu có đường đi chưa khớp mẫu.
~~~

Trong ví dụ này biến `text` chỉ có thể sử dụng ở nhánh mà compiler biết mẫu khớp. Không dùng tên biến mẫu như một biến cục bộ được khai báo vô điều kiện ở đầu phương thức.

## 4. Kết hợp `instanceof` với `&&`: vế phải biết mẫu đã khớp

Toán tử `&&` đánh giá từ trái sang phải và **ngắn mạch**: nếu vế trái `false`, vế phải không chạy.

~~~java
static boolean isLongString(Object value) {
    return value instanceof String text && text.length() >= 4;
}
~~~

Vế phải `text.length() >= 4` chỉ được đánh giá khi `value instanceof String text` đúng, nên tại đó `text` chắc chắn đã khớp mẫu.

| `value` | Nhánh `instanceof` | Vế phải `text.length()` | Kết quả |
|---|---|---|---|
| `"Java"` | true | Được đánh giá: 4 | true |
| `"Go"` | true | Được đánh giá: 2 | false |
| `Integer.valueOf(7)` | false | **Bỏ qua** | false |
| `null` | false | **Bỏ qua** | false |

Có thể thêm guard hoặc phương thức chỉ dùng biến mẫu sau khi khớp:

~~~java
if (value instanceof String text
        && !text.isBlank()
        && text.length() <= 20) {
    System.out.println(text.strip());
}
~~~

**Thứ tự là quan trọng:** đặt kiểm tra mẫu ở bên trái, rồi mới dùng biến mẫu ở những điều kiện tiếp theo.

## 5. Vì sao `||` với kiểm tra mẫu dương không cho dùng biến ở vế phải?

Xét ý định sai:

~~~java
// if (value instanceof String text || text.isEmpty()) {
//     System.out.println("accepted");
// }
~~~

Đây là **lỗi biên dịch**, không phải đơn thuần nguy cơ `NullPointerException`.

Với `A || B`, Java chỉ đánh giá `B` nếu `A` là **false**. Nhưng `A` ở đây chính là `value instanceof String text`, nên khi đi tới `B`, mẫu **chưa khớp**. Vì thế biến `text` không thuộc phạm vi hợp lệ ở vế phải.

**Đối chiếu:**

- `value instanceof String text && text.length() > 0` — **hợp lệ**.
- `value instanceof String text || text.length() > 0` — **không hợp lệ**.
- `value instanceof String text` đứng độc lập trong `if` — biến chỉ dùng được ở nhánh chắc chắn đã khớp.

Đừng tìm cách sửa lỗi biên dịch bằng cast tùy tiện ở vế phải; trước hết hãy xác định xem yêu cầu nghiệp vụ là “và” hay “hoặc”.

## 6. Phủ định `!` làm thay đổi luồng xác định phạm vi

Với biểu thức phủ định:

~~~java
if (!(value instanceof String text)) {
    System.out.println("not a String");
} else {
    System.out.println(text.length()); // Hợp lệ.
}
~~~

Ở nhánh `else`, phủ định `!(...)` là false, tức pattern bên trong **đã khớp**. Vì vậy compiler cho dùng `text`.

Ngược lại:

~~~java
if (!(value instanceof String text)) {
    // System.out.println(text.length()); // Không hợp lệ.
}
~~~

Trong nhánh `if` này, phép kiểm tra kiểu **không khớp**, nên không có biến `text` hợp lệ.

**Mẹo suy luận:** thay vì chỉ nhìn dấu ngoặc, hỏi: *để chương trình đi tới câu lệnh đang xét, biểu thức `instanceof` bắt buộc đã true hay chưa?*

## 7. Guard clause: biến mẫu có thể dùng sau câu `if`

Một mẫu rất hữu ích trong phương thức:

~~~java
static String describe(Object value) {
    if (!(value instanceof String text)) {
        return "NOT_STRING";
    }

    return "TEXT:" + text.strip();
}
~~~

Nếu giá trị không phải String, phương thức **trả về ngay**. Do đó, để đi tới câu `return "TEXT:" + text.strip()`, phép `instanceof` bắt buộc đã đúng, nên `text` thuộc phạm vi hợp lệ **ngay cả sau dấu `}` của `if`**.

Điều đó cho thấy flow scoping **không chỉ là phạm vi từ dấu ngoặc mở đến dấu ngoặc đóng**.

~~~java
if (!(value instanceof String text)) {
    return "NOT_STRING";
}
// text có thể được dùng tại đây vì nhánh thất bại đã return.
~~~

Nhưng nếu nhánh thất bại chỉ ghi log rồi tiếp tục:

~~~java
if (!(value instanceof String text)) {
    System.out.println("not a String");
}
// System.out.println(text.length()); // LỖI: đường đi thất bại vẫn tới đây.
~~~

**Lưu ý:** trong cùng phương thức, đoạn có `return` phải phù hợp kiểu trả về. Các ví dụ với `return` được đặt trong phương thức trả `String`.

## 8. `||` có thể hợp lệ khi kiểm tra bị phủ định

Biểu thức sau là **hợp lệ**:

~~~java
static String validate(Object value) {
    if (!(value instanceof String text) || text.isBlank()) {
        return "INVALID";
    }
    return "VALID:" + text.strip();
}
~~~

Giải thích theo luồng:

1. `!(value instanceof String text)` true nếu không phải String; do `||` ngắn mạch nên **không gọi** `text.isBlank()`.
2. Nếu vế trái false, `value instanceof String text` đã true; vì thế vế phải được phép dùng `text`.
3. Sau câu `if`, chỉ có thể đi tiếp khi toàn bộ điều kiện là false. Điều này bảo đảm `text` là String và **không blank**, nên `text.strip()` an toàn.

| Đầu vào | Kết quả |
|---|---|
| `" Java "` | `VALID:Java` |
| `"   "` | `INVALID` |
| `Integer.valueOf(3)` | `INVALID` |
| `null` | `INVALID` |

So sánh nhanh:

~~~java
// Hợp lệ: kiểm tra mẫu dương và dùng &&.
value instanceof String text && !text.isBlank()

// Hợp lệ: phủ định mẫu, rồi dùng || trong guard.
!(value instanceof String text) || text.isBlank()

// Sai: mẫu dương với ||, dùng text ở vế phải.
// value instanceof String text || text.isBlank()
~~~

Cơ chế của compiler dựa trên **luồng logic** chứ không dựa vào việc biến có tên giống nhau trong biểu thức.

## 9. Dùng `||` để trả boolean khi không phải String hoặc chuỗi rỗng

~~~java
static boolean emptyOrNotString(Object value) {
    return !(value instanceof String text) || text.isEmpty();
}
~~~

Kết quả:

- `42` → true vì không phải String; vế phải được bỏ qua.
- `""` → true vì là String rỗng.
- `"ok"` → false vì là String không rỗng.

Trong ví dụ này, yêu cầu là “**không phải String HOẶC String rỗng**”, nên dùng `||` với mẫu bị phủ định là phù hợp.

**Không suy ra** rằng mọi trường hợp `||` đều cho phép dùng biến mẫu. Điều quyết định là khi chương trình bắt buộc phải đánh giá vế phải, pattern đã **definitely matched** hay chưa.

## 10. Biến mẫu trong toán tử điều kiện `?:`

Toán tử ba ngôi cũng có nhánh true/false rõ ràng:

~~~java
static int stringLengthOrMinusOne(Object value) {
    return value instanceof String text ? text.length() : -1;
}
~~~

Nhánh true của `?:` chỉ chạy khi `value instanceof String text` đúng nên `text.length()` hợp lệ. Nhánh false không có bảo đảm mẫu khớp:

~~~java
// int n = value instanceof String text
//         ? text.length()
//         : text.length(); // Không hợp lệ ở nhánh false.
~~~

Trường hợp này phù hợp khi biểu thức trả kết quả đơn giản. Với nhiều lệnh kiểm tra hoặc cần báo lỗi, một `if/else` rõ ràng có thể dễ đọc hơn.

## 11. Scope trong `else if` và biến mẫu khác

~~~java
static String describeValue(Object value) {
    if (value instanceof String text && !text.isBlank()) {
        return "TEXT:" + text.strip();
    } else if (value instanceof Integer number) {
        return "INTEGER:" + (number + 1);
    }
    return "OTHER";
}
~~~

- `text` chỉ hợp lệ trong những vị trí được nhánh kiểm tra String đảm bảo.
- `number` chỉ hợp lệ trong nhánh kiểm tra Integer.
- Không được dùng `text` ở nhánh Integer khi điều kiện String có thể false.
- Sau toàn bộ chuỗi, không có bảo đảm một trong hai mẫu đã khớp, nên các biến mẫu không trở thành biến chung của phương thức.

**Lưu ý về điều kiện phụ:** một String có thể khớp `instanceof String text` nhưng bị loại do `text.isBlank()`. Khi đó nó không tự trở thành Integer; cuối cùng ví dụ trả `OTHER`.

Trong các biểu thức phức tạp, việc dùng cùng tên biến cho nhiều mẫu có phạm vi chồng lấn có thể gây lỗi khai báo trùng. Hãy đặt tên theo kiểu hoặc ý nghĩa (`text`, `number`) và giữ điều kiện dễ đọc.

## 12. Các giới hạn và điểm cần tránh

| Trường hợp | Hợp lệ? | Nguyên nhân |
|---|---|---|
| `if (o instanceof String s) { s.length(); }` | Có | Mẫu khớp ở nhánh true |
| `if (o instanceof String s && s.length() > 2)` | Có | Vế phải chỉ chạy khi mẫu khớp |
| `if (o instanceof String s || s.length() > 2)` | **Không** | Vế phải chỉ chạy khi mẫu không khớp |
| `if (!(o instanceof String s)) { return; } use(s);` | Có, trong phương thức `void` phù hợp | Đường đi chưa khớp đã thoát |
| `if (!(o instanceof String s)) { log(); } use(s);` | **Không** | Nhánh không khớp vẫn có thể đi tiếp |
| `if (!(o instanceof String s)) ... else use(s);` | Có | Nhánh else bảo đảm mẫu khớp |
| `!(o instanceof String s) || s.isEmpty()` | Có | Muốn đánh giá vế phải phải có mẫu khớp |
| `o instanceof String s ? s.length() : -1` | Có | Nhánh true bảo đảm khớp |
| `o instanceof String s ? 0 : s.length()` | **Không** | Nhánh false không bảo đảm khớp |

Một số điểm Java 17 đáng nhớ:

- Với `Object candidate = null;`, biểu thức `candidate instanceof String s` trả `false` mà không ném NPE; không có biến mẫu `s` trên đường đi đó. **Không viết trực tiếp literal `null instanceof String s` trong Java 17**: đó là type pattern bị từ chối khi biên dịch (mẫu vô điều kiện đối với kiểu của literal `null`). Kiểm tra kiểu không ràng buộc mẫu `null instanceof String` lại hợp lệ và cho `false`.
- Không dùng `switch` pattern matching như một tính năng ổn định mặc định của Java 17: ở phiên bản này nó thuộc **preview**; bài dùng `instanceof` pattern ổn định, biên dịch `--release 17` bình thường.
- Compiler thường từ chối mẫu `instanceof` hiển nhiên luôn đúng về mặt kiểu tĩnh (ví dụ biến đã khai báo `String` lại thử `instanceof String s`). Dùng mẫu kiểm tra kiểu tại nơi biểu thức có kiểu đủ rộng, như `Object` hoặc interface phù hợp.
- Chỉ dùng biến mẫu trong luồng đã xác nhận kiểu; đừng ép cast hoặc khai báo lại biến chỉ để “qua compiler” nếu biểu thức logic thực sự sai.

## 13. Chương trình Java 17 hoàn chỉnh

Tạo file đúng tên **`PatternFlowScopeDemo.java`**:

~~~java
import java.util.Locale;

public final class PatternFlowScopeDemo {
    private PatternFlowScopeDemo() {}

    static String inspect(Object input) {
        if (input instanceof String text && !text.isBlank()) {
            return "TEXT:" + text.strip().toUpperCase(Locale.ROOT);
        } else if (input instanceof Integer number) {
            return "INTEGER:" + (number + 1);
        }
        return "OTHER";
    }

    static String positiveBranch(Object value) {
        if (value instanceof String text) {
            return "STRING:" + text.length();
        }
        return "NOT_STRING";
    }

    static boolean longEnough(Object value) {
        return value instanceof String text && text.length() >= 4;
    }

    static String afterGuard(Object value) {
        if (!(value instanceof String text)) {
            return "NO_STRING";
        }
        return "AFTER_GUARD:" + text.length();
    }

    static String rejectInvalid(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return "REJECT";
        }
        return "ACCEPT:" + text.strip();
    }

    static boolean emptyOrNotString(Object value) {
        return !(value instanceof String text) || text.isEmpty();
    }

    static int conditionalLength(Object value) {
        return value instanceof String text ? text.length() : -1;
    }

    static String negativeElse(Object value) {
        if (!(value instanceof String text)) {
            return "ELSE:NO";
        } else {
            return "ELSE:YES:" + text.length();
        }
    }

    static boolean traced(String label, boolean answer) {
        System.out.println("checked " + label);
        return answer;
    }

    static boolean tracedAnd(Object value) {
        return value instanceof String text
                && traced("length", text.length() >= 2);
    }

    public static void main(String[] args) {
        Object[] inputs = {
            " Java ", "   ", Integer.valueOf(41), null, "Go"
        };
        for (Object value : inputs) {
            System.out.println("inspect=" + inspect(value));
        }

        System.out.println("positive String=" + positiveBranch("Hi"));
        System.out.println("positive Integer=" + positiveBranch(5));

        System.out.println("long Java=" + longEnough("Java"));
        System.out.println("long Go=" + longEnough("Go"));
        System.out.println("long null=" + longEnough(null));

        System.out.println("guard String=" + afterGuard("abc"));
        System.out.println("guard Integer=" + afterGuard(5));

        System.out.println("reject blank=" + rejectInvalid("  "));
        System.out.println("reject number=" + rejectInvalid(5));
        System.out.println("accept text=" + rejectInvalid(" x "));

        System.out.println("empty non-string=" + emptyOrNotString(5));
        System.out.println("empty String=" + emptyOrNotString(""));
        System.out.println("nonempty String=" + emptyOrNotString("ok"));

        System.out.println("length hello=" + conditionalLength("hello"));
        System.out.println("length other=" + conditionalLength(5));

        System.out.println("negated else String=" + negativeElse("abc"));
        System.out.println("negated else null=" + negativeElse(null));

        System.out.println("traced Integer=" + tracedAnd(5));
        System.out.println("traced String=" + tracedAnd("Hi"));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục chứa file; không dùng thư viện ngoài JDK, Maven, Gradle hoặc tính năng preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 PatternFlowScopeDemo.java
java -cp . PatternFlowScopeDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\PatternFlowScopeDemo.java
java -cp . PatternFlowScopeDemo
~~~

### Exact expected stdout

~~~text
inspect=TEXT:JAVA
inspect=OTHER
inspect=INTEGER:42
inspect=OTHER
inspect=TEXT:GO
positive String=STRING:2
positive Integer=NOT_STRING
long Java=true
long Go=false
long null=false
guard String=AFTER_GUARD:3
guard Integer=NO_STRING
reject blank=REJECT
reject number=REJECT
accept text=ACCEPT:x
empty non-string=true
empty String=true
nonempty String=false
length hello=5
length other=-1
negated else String=ELSE:YES:3
negated else null=ELSE:NO
traced Integer=false
checked length
traced String=true
~~~

**Vì sao output như vậy?**

1. `inspect` chỉ dùng `text` khi mẫu String khớp và chuỗi không blank; `Integer` được xử lý bằng biến `number` ở nhánh riêng.
2. `longEnough` minh họa `&&` cho phép truy cập `text.length()` ở vế phải và bỏ qua vế phải với null hoặc Integer.
3. `afterGuard` dùng được `text` sau `if`, do đường đi không khớp đã `return`.
4. `rejectInvalid` dùng `||` với phủ định pattern và từ chối String blank; sau guard chỉ còn chuỗi hợp lệ.
5. `emptyOrNotString` trả true với object không phải String hoặc chuỗi rỗng; bỏ qua `text.isEmpty()` nếu không khớp.
6. `conditionalLength` dùng biến mẫu trong nhánh true của `?:`.
7. `negativeElse` dùng `text` ở nhánh `else` khi phép phủ định false.
8. `tracedAnd(5)` **không in `checked length`** vì `instanceof` false; `tracedAnd("Hi")` mới gọi `traced`.

## 14. Lỗi phổ biến và cách sửa

| Sai lầm | Lý do | Cách sửa |
|---|---|---|
| Dùng biến mẫu ở `else` của kiểm tra dương | Nhánh else có thể không khớp | Dùng biến ở nhánh true |
| Viết `o instanceof String s || s.isBlank()` | Vế phải của OR chỉ chạy khi mẫu không khớp | Dùng `&&` cho nhánh dương, hoặc `!(...) || ...` nếu logic yêu cầu |
| Dùng `s` sau `if` nhưng nhánh không khớp vẫn chạy tiếp | Không bảo đảm definitely matched | Guard clause `return`/`throw` ở nhánh thất bại |
| Tưởng dấu `{ }` là quy tắc duy nhất xác định scope | Java dùng phân tích luồng điều kiện | Xét các đường đi true/false |
| Dùng `s` trong nhánh true của `!(o instanceof String s)` | Nhánh đó nghĩa là không khớp | Dùng trong `else` hoặc sau guard thoát sớm |
| Dùng `s` ở nhánh false của `?:` kiểm tra dương | Không bảo đảm mẫu khớp | Chỉ dùng trong nhánh true |
| Nghĩ `null instanceof String s` sẽ ném NPE | `instanceof` trả false với null | Có thể dùng như một null-safe type guard |
| Viết cast lặp dù đã có pattern variable | Dễ dư code/cast sai | Dùng trực tiếp biến mẫu ở phạm vi hợp lệ |
| Giấu side effect trong điều kiện kiểu | Khó suy luận luồng/đầu ra | Tách kiểm tra và side effect khi cần |
| Dùng pattern switch preview mà không bật cờ trên JDK 17 | Lỗi compile/tính năng chưa ổn định | Dùng `instanceof` pattern ổn định |

## 15. Bài tập có đáp án

### Bài A — biến mẫu trong `&&`

~~~java
Object value = "Java";
boolean valid = value instanceof String text && text.length() == 4;
System.out.println(valid);
~~~

**Đáp án:** in `true`. Vế phải chỉ chạy khi pattern khớp.

### Bài B — tại sao `||` sai?

~~~java
// boolean bad = value instanceof String text || text.isEmpty();
~~~

**Đáp án:** nếu vế phải của `||` được đánh giá, pattern ở vế trái đã false; `text` chưa khớp chắc chắn nên không thuộc phạm vi. Sửa tùy ý định:

~~~java
boolean nonempty = value instanceof String text && !text.isEmpty();
~~~

Hoặc khi muốn “không phải String **hoặc** String rỗng”:

~~~java
boolean emptyOrWrongType =
        !(value instanceof String text) || text.isEmpty();
~~~

### Bài C — dùng biến sau guard clause

~~~java
static String uppercase(Object value) {
    if (!(value instanceof String text)) {
        return "UNKNOWN";
    }
    return text.toUpperCase(Locale.ROOT);
}
~~~

**Đáp án:** hợp lệ, vì đường đi thất bại đã `return`. Với `value = "hi"`, trả `"HI"`.

### Bài D — so sánh hai cấu trúc phủ định

~~~java
if (!(value instanceof String text)) {
    System.out.println("not a String");
} else {
    System.out.println(text.length());
}
~~~

**Đáp án:** `text` dùng được trong `else`, vì tới `else` có nghĩa pattern đã khớp.

### Bài E — toán tử điều kiện

~~~java
Object value = "abc";
int length = value instanceof String text ? text.length() : -1;
System.out.println(length);
~~~

**Đáp án:** `3`. Biến `text` thuộc phạm vi nhánh true của `?:`, không thuộc phạm vi nhánh false.

## 16. Checklist và kết luận

- [ ] Biết cú pháp `object instanceof Type variable` của Java 17.
- [ ] Hiểu biến mẫu chỉ tồn tại ở luồng mà compiler chứng minh pattern khớp.
- [ ] Dùng biến mẫu ở nhánh true của `if (o instanceof T t)`.
- [ ] Hiểu `&&` cho dùng biến mẫu ở vế phải của kiểm tra dương.
- [ ] Biết vì sao `||` với kiểm tra dương không cho dùng biến mẫu ở vế phải.
- [ ] Hiểu phủ định `!` thay đổi nhánh nào bảo đảm pattern khớp.
- [ ] Dùng guard `if (!(o instanceof T t)) return/throw` để dùng `t` sau guard.
- [ ] Biết `!(o instanceof T t) || ... t ...` có thể hợp lệ với short-circuit.
- [ ] Biết biến mẫu dùng được ở nhánh true của toán tử `?:` kiểm tra dương.
- [ ] Không sử dụng biến mẫu ở đường đi thất bại hoặc sau `if` khi đường đi thất bại còn tiếp tục.
- [ ] Biết `instanceof` trên null trả false, không ném NPE.
- [ ] Biên dịch và chạy `PatternFlowScopeDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Phạm vi biến mẫu trong Java 17 được quyết định bởi **luồng điều khiển và thông tin kiểu mà compiler chứng minh được**. Với `instanceof` dương, biến có sẵn ở nhánh đúng và vế phải `&&`; với phủ định, biến có thể xuất hiện ở `else`, vế phải của `||` phù hợp hoặc sau guard clause thoát sớm. Hiểu flow scoping giúp giảm cast thừa mà không sử dụng biến ở đường đi chưa được kiểm tra kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-004">Kết hợp instanceof pattern với &amp;&amp; hoặc guard clause</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp `instanceof` pattern với `&&` hoặc guard clause trong Java 17

> **Java core — language-basics / Mẫu kiểm tra kiểu**  
> **Môi trường:** Java 17+, một file tự chứa, chỉ cần JDK; không bật preview.  
> **Mục tiêu:** Kết hợp phép kiểm tra kiểu với điều kiện phụ bằng `&&`; dùng guard clause thoát sớm để sử dụng biến mẫu phía sau; hiểu *flow scoping*, short-circuit và các lỗi về phạm vi biến.

## 1. Tình huống thực tế: kiểm tra rồi xử lý dữ liệu nhập

Giả sử một hàm nhận `Object` từ API và chỉ chấp nhận chuỗi có nội dung. Type pattern `input instanceof String text` vừa kiểm tra kiểu runtime vừa ràng buộc biến `text`; nếu là `null` hoặc kiểu khác, mẫu không khớp.

~~~java
static boolean hasUsableName(Object input) {
    return input instanceof String name
            && !name.isBlank()
            && name.strip().length() <= 20;
}
~~~

Hàm trả `false` với `null`, số nguyên, chuỗi toàn khoảng trắng hoặc chuỗi sau `strip()` dài hơn 20 ký tự. Với tên hợp lệ, các điều kiện sau mới được đánh giá. Chính thứ tự ấy giúp không cần ép kiểu hoặc kiểm tra null riêng.

## 2. Chọn `&&` hay guard clause theo nhiệm vụ

Nếu chỉ cần một câu trả lời boolean như `hasUsableName`, nối điều kiện bằng `&&` giúp tránh nhánh lồng nhau. Khi cần giải thích **vì sao** từ chối (sai kiểu, chuỗi rỗng, dữ liệu quá dài) hoặc dùng đối tượng đã kiểm tra qua nhiều bước, guard clause thường dễ đọc hơn. Mục 4 so sánh các đường trả về; chương trình hoàn chỉnh ở mục 7 đếm số lần một điều kiện phụ thực sự được gọi.

| Mục tiêu | Cách viết |
| --- | --- |
| Quy tắc kiểm tra ngắn, không cần lý do từ chối | Type pattern kết hợp `&&` |
| Trả mã lỗi khác nhau và xử lý nhiều bước | Guard `if (!(x instanceof T t)) return ...;` |
| Trả cùng một lỗi nếu sai kiểu **hoặc** không đạt điều kiện phụ | Guard phủ định kết hợp `||` |

Đối với cả hai cách, biến mẫu chỉ được dùng khi luồng đã bảo đảm khớp kiểu.

## 3. Khi thay `&&` bằng `&` hoặc `||`

`&&` ngắn mạch: không kiểm tra điều kiện phụ nếu mẫu sai. Toán tử `&` trên `boolean` đánh giá cả hai vế nên `input instanceof String s & !s.isBlank()` không biên dịch: compiler không thể bảo đảm `s` đã được ràng buộc ở vế phải.

~~~java
Object input = " Java ";
// Hợp lệ: điều kiện phụ chỉ chạy sau khi String đã khớp.
boolean ok = input instanceof String s && !s.isBlank();

// Không hợp lệ: OR dương xét vế phải khi mẫu thất bại.
// boolean bad = input instanceof String s || s.isBlank();
~~~

Với yêu cầu “sai kiểu **hoặc** chuỗi blank”, đảo điều kiện mẫu sẽ làm `||` phù hợp: `!(input instanceof String text) || text.isBlank()`. Trong trường hợp đó vế phải chỉ chạy khi mẫu String thật sự khớp. Chương trình ở mục 7 đặt ba cách cạnh nhau để thấy khác biệt về kết quả và số lần gọi hàm.

## 4. Guard clause: trả về sớm khi không đúng kiểu

Khi phải xử lý nhiều bước sau kiểm tra kiểu, dùng guard clause thường dễ đọc hơn nhiều tầng lồng nhau:

~~~java
static String normalize(Object input) {
    if (!(input instanceof String text)) {
        return "NOT_STRING";
    }

    if (text.isBlank()) {
        return "BLANK";
    }

    return text.trim().toUpperCase(java.util.Locale.ROOT);
}
~~~

Nếu không phải String hoặc là null, nhánh đầu **return** ngay. Để đến được dòng `text.isBlank()`, Java biết pattern phải đã khớp, nên biến mẫu `text` có thể sử dụng ở phần còn lại.

Nếu thay `return` trong guard đầu bằng lệnh chỉ in log rồi cho chương trình chạy tiếp, Java **không thể đảm bảo** `text` đã được ràng buộc ở các dòng sau.

| Cần làm | Cách phù hợp |
| --- | --- |
| Một điều kiện kiểu và một điều kiện phụ ngắn | `if (x instanceof String s && !s.isBlank())` |
| Phân biệt lỗi sai kiểu và chuỗi trống | Guard phủ định, rồi kiểm tra blank |
| Nhiều thao tác liên tiếp trên đối tượng đã kiểm tra | Guard clause để giảm lồng nhánh |
| Không cần sử dụng biến mẫu | `x instanceof String` là đủ |

## 5. Guard phủ định kết hợp OR và early return

Có thể kết hợp cả hai bước từ chối:

~~~java
static String accept(Object input) {
    if (!(input instanceof String text) || text.isBlank()) {
        return "REJECTED";
    }
    return "ACCEPTED:" + text.trim();
}
~~~

Ở vế phải của OR, `text` hợp lệ vì khi vế đó được xét, pattern đã khớp. Nếu toàn bộ điều kiện true thì phương thức return. Nếu luồng đi tiếp, Java biết `input` là String và `text` không blank.

Dù viết gọn, cách này có thể khó đọc khi nhiều điều kiện; khi cần trả thông báo lỗi riêng từng nguyên nhân, tách thành hai guard như mục 4.

Ví dụ với interface:

~~~java
static String longSequence(Object input) {
    if (input instanceof CharSequence seq && seq.length() >= 3) {
        return "LEN:" + seq.length();
    }
    return "SHORT_OR_OTHER";
}
~~~

Cả `String` lẫn `StringBuilder` đều triển khai `CharSequence`. Pattern có thể dùng với lớp hoặc interface, không cần chỉ kiểm tra `String`.

## 6. Lỗi thường gặp và giới hạn Java 17

- **Tham chiếu `null`:** Với `Object missing = null;`, phép `missing instanceof String s` trả `false`; biến mẫu `s` không được dùng trên đường đi đó. **Literal `null instanceof String s` không biên dịch ở Java 17**, vì pattern này vô điều kiện theo kiểu tĩnh của literal. Dùng một biến tham chiếu có giá trị null như trong ví dụ thay vì viết literal trực tiếp.
- **Dùng biến mẫu ở sai chỗ:** Sau `if (x instanceof String s)` thông thường, đừng tự giả định `s` có thể dùng ở ngoài nhánh; hãy để một guard có `return` hoặc `throw` loại hết đường không khớp.
- **Pattern thừa:** `String alreadyString = "x";` rồi `alreadyString instanceof String s` có thể bị từ chối trong Java 17 vì kiểu tĩnh đã là subtype của kiểu mẫu. Không cần kiểm tra kiểu đã biết.
- **Generic bị xóa:** Không thể kiểm tra trực tiếp `instanceof java.util.List<String>`; dùng kiểu kiểm tra được tại runtime như `List<?>` và kiểm tra các phần tử riêng nếu cần.
- **Điều kiện có tác dụng phụ:** Vế phải của `&&` không được gọi khi vế trái false. Nếu vế phải cập nhật biến, ghi log hoặc ném ngoại lệ, chuyển sang toán tử khác có thể thay đổi hành vi.
- **Không thay kiểm tra nghiệp vụ:** Khớp String không có nghĩa chuỗi không rỗng, đúng định dạng hoặc không vượt giới hạn.

Nguyên tắc: giữ biểu thức ngắn, ưu tiên tên biến rõ nghĩa, và dùng guard clause cho nhiều điều kiện từ chối hoặc nhiều bước xử lý.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **InstanceofAndGuardDemo.java**:

~~~java
import java.util.Locale;

public final class InstanceofAndGuardDemo {
    private static int checkCalls;

    private static boolean nonBlank(String text) {
        checkCalls++;
        return !text.isBlank();
    }

    private static String withAnd(Object input) {
        if (input instanceof String text && nonBlank(text)) {
            return "TEXT:" + text.trim().toUpperCase(Locale.ROOT);
        }
        return "NO_TEXT";
    }

    private static String withGuard(Object input) {
        if (!(input instanceof String text)) {
            return "NOT_STRING";
        }
        if (text.isBlank()) {
            return "BLANK";
        }
        return "TEXT:" + text.trim().toUpperCase(Locale.ROOT);
    }

    private static String withOrGuard(Object input) {
        if (!(input instanceof String text) || text.isBlank()) {
            return "REJECTED";
        }
        return "ACCEPTED:" + text.trim();
    }

    private static String sequenceLabel(Object input) {
        if (input instanceof CharSequence seq && seq.length() >= 3) {
            return "LEN:" + seq.length();
        }
        return "SHORT_OR_OTHER";
    }

    private static String display(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + text + "\"";
        }
        return value.getClass().getSimpleName() + ":" + value;
    }

    public static void main(String[] args) {
        Object[] inputs = {null, Integer.valueOf(7), "  ", " java ", "Hi"};

        for (Object value : inputs) {
            checkCalls = 0;
            String andResult = withAnd(value);
            int checks = checkCalls;
            String guardResult = withGuard(value);
            String orResult = withOrGuard(value);

            System.out.println("input=" + display(value)
                    + ";and=" + andResult
                    + ";checks=" + checks
                    + ";guard=" + guardResult
                    + ";or=" + orResult);
        }

        System.out.println("sequenceBuilder="
                + sequenceLabel(new StringBuilder("abc")));
        System.out.println("sequenceShort=" + sequenceLabel("Hi"));
        System.out.println("sequenceNull=" + sequenceLabel(null));
    }
}
~~~

Biên dịch và chạy ở thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 InstanceofAndGuardDemo.java
java InstanceofAndGuardDemo
~~~

**Expected stdout chính xác:**

~~~text
input=null;and=NO_TEXT;checks=0;guard=NOT_STRING;or=REJECTED
input=Integer:7;and=NO_TEXT;checks=0;guard=NOT_STRING;or=REJECTED
input="  ";and=NO_TEXT;checks=1;guard=BLANK;or=REJECTED
input=" java ";and=TEXT:JAVA;checks=1;guard=TEXT:JAVA;or=ACCEPTED:java
input="Hi";and=TEXT:HI;checks=1;guard=TEXT:HI;or=ACCEPTED:Hi
sequenceBuilder=LEN:3
sequenceShort=SHORT_OR_OTHER
sequenceNull=SHORT_OR_OTHER
~~~

`checks=0` khi null hoặc Integer cho thấy hàm `nonBlank` **không chạy** khi pattern thất bại. Với chuỗi blank, pattern khớp nên `nonBlank` chạy đúng một lần rồi trả false. Guard clause cho nhãn từ chối cụ thể; guard dùng OR cho một nhãn từ chối chung.

## 8. Bài tập tự luyện

**Bài 1.** Dự đoán điều kiện `x instanceof String s && s.length() >= 5` với `null`, `Integer`, chuỗi 2 ký tự và chuỗi 6 ký tự; vế phải chạy khi nào?

**Bài 2.** Viết lại bài 1 bằng guard clause, sau đó dùng `s` ngoài nhánh guard khi bảo đảm đã khớp kiểu.

**Bài 3.** Giải thích vì sao kiểm tra kiểu rồi nối với OR trực tiếp không thể dùng biến mẫu ở vế phải, nhưng phủ định kiểm tra kiểu rồi OR với `s.isBlank()` lại hợp lệ.

**Bài 4.** Viết một bộ đếm trong hàm điều kiện phụ; thử input null, sai kiểu, String rỗng và String hợp lệ để quan sát số lần gọi.

**Bài 5.** Dùng pattern `CharSequence seq` với `StringBuilder("abcd")` và `String("xy")`; xác định đầu ra khi yêu cầu độ dài ít nhất 3.

### Ghi nhớ

**`x instanceof T name && predicate(name)`** an toàn vì vế phải chỉ chạy sau khi pattern khớp. **`if (!(x instanceof T name)) return ...;`** là guard clause giúp sử dụng `name` ở phần còn lại của phương thức. Phạm vi biến mẫu được kiểm tra theo luồng thực thi, không chỉ theo dấu ngoặc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-005">Pattern matching cho switch trong Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# Pattern matching cho `switch` trong Java 21

**Pattern matching for switch** là tính năng chuẩn từ **Java 21 (JEP 441)**: thay vì chỉ so sánh `case` với hằng số, Java có thể kiểm tra **kiểu thực tế** của đối tượng, ràng buộc đối tượng với **biến mẫu** và dùng điều kiện bổ sung `when`.

~~~java
Object value = "Java";

String result = switch (value) {
    case String s -> "Chuỗi: " + s.toUpperCase();
    case Integer n -> "Số: " + n;
    case null -> "Thiếu dữ liệu";
    default -> "Kiểu khác";
};
~~~

Nếu `value` là `"Java"`, kết quả là `"Chuỗi: JAVA"`. Khi nhãn `case String s` khớp, `s` có kiểu tĩnh `String`, nên không cần viết `(String) value`. **Mẫu `case String s` không đồng nghĩa `case "String"`**: một bên kiểm tra kiểu, một bên là nhãn hằng chuỗi.

**Lưu ý phiên bản:** `instanceof String s` đã là tính năng chuẩn từ Java 16 và dùng được ở JDK 17. Nhưng **pattern matching trong `switch` là tính năng chuẩn từ JDK 21**. Ví dụ `case String s ->` dưới đây **không biên dịch với `javac` JDK 17 tiêu chuẩn**. Mục 3 có cách viết tương đương chạy bằng JDK 17, không cần preview.

## 1. Kiểu mẫu, `when`, `null` và `default`

Ví dụ:

~~~java
return switch (value) {
    case null -> "missing";
    case String s when s.isBlank() -> "blank";
    case String s -> "text:" + s;
    case Integer n when n >= 0 -> "nonnegative:" + n;
    case Integer n -> "negative:" + n;
    default -> "other";
};
~~~

Quy tắc:

- `case String s`: khớp khi đối tượng có kiểu `String` tương thích; ràng buộc nó với `s`, chỉ có hiệu lực trong nhánh tương ứng.
- `case String s when s.isBlank()`: trước tiên phải khớp kiểu `String`, sau đó kiểm tra thêm điều kiện `when`. Nếu guard false, Java tiếp tục xét nhãn phía sau.
- `case null`: **xử lý selector null**. Đây là điểm khác đáng chú ý so với switch truyền thống Java 17.
- `default`: xử lý trường hợp không nhãn nào khớp. **`default` riêng lẻ không bắt `null`**; nếu thiếu `case null`, selector null thông thường ném `NullPointerException`.
- `case ... ->`: thực thi đúng nhánh đã chọn, **không fall-through** xuống nhãn sau.

| Selector | Nhãn được chọn trong ví dụ trên | Kết quả |
| --- | --- | --- |
| `null` | `case null` | `missing` |
| `"   "` | `String s when s.isBlank()` | `blank` |
| `"Java"` | `String s` | `text:Java` |
| `12` (Integer) | `Integer n when n >= 0` | `nonnegative:12` |
| `-2` (Integer) | `Integer n` | `negative:-2` |
| `2.5` (Double) | `default` | `other` |

### Thứ tự nhãn và dominance

Nếu `case String s` **không có guard** xuất hiện trước `case String s when s.isBlank()`, nhãn thứ hai sẽ bị bao phủ bởi nhãn thứ nhất và gây lỗi **dominated case**. Do đó đặt mẫu **hẹp/có điều kiện** trước mẫu **rộng/không có điều kiện**.

Không dùng `when` sau một nhãn hằng như thể nó là phép `if` tổng quát; cú pháp guarded pattern được thiết kế để đi cùng **pattern label**.

## 2. Ví dụ hoàn chỉnh chạy với JDK 21

Tạo file UTF-8 **`PatternSwitch21Demo.java`**:

~~~java
import java.util.Locale;

public class PatternSwitch21Demo {
    sealed interface Action permits Start, Stop {}
    record Start(String task) implements Action {}
    record Stop() implements Action {}

    static String describe(Object value) {
        return switch (value) {
            case null -> "missing";
            case String s when s.isBlank() -> "blank";
            case String s -> "text:" + s.toUpperCase(Locale.ROOT);
            case Integer n when n >= 0 -> "nonnegative:" + n;
            case Integer n -> "negative:" + n;
            case Start s -> "start:" + s.task();
            case Stop s -> "stop";
            default -> "other";
        };
    }

    static String action(Action action) {
        return switch (action) {
            case Start s -> "start:" + s.task();
            case Stop s -> "stop";
        };
    }

    public static void main(String[] args) {
        Object[] inputs = {
            null, "   ", "java", 12, -2,
            new Start("build"), new Stop(), 2.5
        };
        for (Object input : inputs) {
            System.out.println(describe(input));
        }
        System.out.println("sealed=" + action(new Start("deploy")));
    }
}
~~~

Dùng **JDK 21 trở lên** trong thư mục chứa file (PowerShell hoặc Bash):

~~~bash
java -version
javac -version
javac -encoding UTF-8 PatternSwitch21Demo.java
java PatternSwitch21Demo
~~~

**Exact expected stdout** của riêng `java PatternSwitch21Demo`:

~~~text
missing
blank
text:JAVA
nonnegative:12
negative:-2
start:build
stop
other
sealed=start:deploy
~~~

Không cần `--enable-preview` trên JDK 21. Đầu ra `java -version` và `javac -version` phụ thuộc bản JDK được cài, nên không tính vào stdout cố định.

### Exhaustiveness với `sealed`

`Action` là một **sealed interface** chỉ cho phép `Start` và `Stop` triển khai trực tiếp. Compiler biết tập subtype được phép, vì vậy switch expression sau có thể **không cần `default`**:

~~~java
static String action(Action action) {
    return switch (action) {
        case Start s -> "start:" + s.task();
        case Stop s -> "stop";
    };
}
~~~

Biểu thức `switch` phải **bao phủ đầy đủ** (*exhaustive*) những giá trị thuộc kiểu selector theo quy tắc biên dịch; nếu có subtype mới được phép mà không cập nhật các nhánh, compiler có thể yêu cầu sửa. `switch` statement với mẫu cũng có các quy tắc bao phủ/nhãn phù hợp, nhưng không nên nhầm với switch statement truyền thống chỉ dùng hằng.

**Trường hợp null:** liệt kê đủ subtype `Start` và `Stop` không có nghĩa là đã xử lý null. Nếu `action == null`, switch này ném `NullPointerException` khi chạy.

## 3. Phương án tương thích Java 17: `instanceof` type pattern

Nếu chỉ có JDK 17, dùng các chuỗi `if` với `instanceof Type name` và guard. Cách này thể hiện cùng logic, chỉ không dùng pattern-switch Java 21.

Tạo file UTF-8 **`PatternSwitch17Compat.java`**:

~~~java
import java.util.Locale;

public class PatternSwitch17Compat {
    sealed interface Action permits Start, Stop {}
    record Start(String task) implements Action {}
    record Stop() implements Action {}

    static String describe(Object value) {
        if (value == null) {
            return "missing";
        }
        if (value instanceof String s) {
            if (s.isBlank()) {
                return "blank";
            }
            return "text:" + s.toUpperCase(Locale.ROOT);
        }
        if (value instanceof Integer n) {
            return n >= 0 ? "nonnegative:" + n : "negative:" + n;
        }
        if (value instanceof Start s) {
            return "start:" + s.task();
        }
        if (value instanceof Stop) {
            return "stop";
        }
        return "other";
    }

    static String action(Action action) {
        if (action instanceof Start s) {
            return "start:" + s.task();
        }
        if (action instanceof Stop) {
            return "stop";
        }
        throw new IllegalArgumentException("unsupported action");
    }

    public static void main(String[] args) {
        Object[] inputs = {
            null, "   ", "java", 12, -2,
            new Start("build"), new Stop(), 2.5
        };
        for (Object input : inputs) {
            System.out.println(describe(input));
        }
        System.out.println("sealed=" + action(new Start("deploy")));
    }
}
~~~

Chạy với **JDK 17 trở lên**:

~~~bash
javac -encoding UTF-8 PatternSwitch17Compat.java
java PatternSwitch17Compat
~~~

**Exact expected stdout** của `java PatternSwitch17Compat`:

~~~text
missing
blank
text:JAVA
nonnegative:12
negative:-2
start:build
stop
other
sealed=start:deploy
~~~

Hai chương trình cho cùng đầu ra minh họa. Tuy nhiên, cách viết Java 17 dùng các kiểm tra `instanceof` tuần tự nên **không có cùng kiểm tra exhaustiveness của switch expression trên sealed type**: phương thức `action` ở bản này sử dụng một `throw` dự phòng.

## 4. Pattern switch, `switch expression` và `yield`

Pattern matching có thể xuất hiện trong cả switch statement lẫn switch expression. Trong chương trình JDK 21, `describe` dùng **switch expression** trả về `String` trực tiếp từ từng nhánh.

Nếu một nhánh cần nhiều câu lệnh, dùng khối và `yield` để tạo giá trị:

~~~java
String result = switch (value) {
    case String s -> {
        String cleaned = s.trim();
        yield "length:" + cleaned.length();
    }
    case null -> "missing";
    default -> "other";
};
~~~

`yield` trả giá trị **từ switch expression**, không thoát toàn bộ phương thức như `return`. Với `case ... ->` đơn, chỉ cần viết biểu thức kết quả, không phải `yield`.

**Kiểu kết quả** được xác định khi biên dịch từ các nhánh/ngữ cảnh đích; không tự thay đổi kiểu ở runtime tùy vào pattern nào khớp. Biến mẫu `s` có kiểu tĩnh `String`, nhưng biến `value` bên ngoài vẫn có kiểu tĩnh `Object`.

## 5. Giới hạn quan trọng và lỗi thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Java 17 hỗ trợ `case String s ->` như Java 21.” | Java 17 chỉ có pattern-switch ở dạng preview, không phải cú pháp chuẩn không bật preview |
| “`case String s` tạo đối tượng String mới.” | Chỉ ràng buộc tham chiếu khi kiểu khớp |
| “`case String s` và `case "String"` giống nhau.” | Một bên là **type pattern**, một bên là hằng chuỗi |
| “`case String s` nên đặt trước `case String s when ...`.” | Mẫu rộng có thể thống trị mẫu hẹp và gây lỗi biên dịch |
| “`default` tự xử lý `null`.” | Cần `case null`, hoặc null có thể gây NPE |
| “`when` chạy trước khi kiểm tra kiểu.” | Guard chỉ được xét sau khi pattern khớp |
| “Dạng `case ->` rơi tiếp xuống nhánh sau.” | Không có fall-through |
| “Khai báo đủ subtype sealed cũng tự bắt null.” | Null phải được xử lý riêng nếu cần |
| “`case List<String> list` luôn kiểm tra được generic argument.” | Type erasure hạn chế kiểm tra kiểu generic tham số hóa; thường dùng `List<?>` rồi xác thực phần tử |

Trong Java 21, record patterns cũng được chuẩn hóa và có thể phối hợp với pattern-switch, nhưng đó là kỹ thuật **phân rã cấu trúc record**, khác với type pattern cơ bản của bài này. Không cần dùng record pattern để thực hiện ví dụ trên.

## 6. Bài tập tự kiểm tra

1. `switch(value) { case String s -> ...; default -> ...; }` khi `value` là `"abc"` sẽ chạy nhánh nào?  
   *Đáp án:* `String s`.
2. Nếu `value == null` và không có `case null`, `default` có chạy không?  
   *Đáp án:* Không; thường ném `NullPointerException`.
3. Có thể đặt `case String s when s.isBlank()` sau `case String s` không?  
   *Đáp án:* Không; mẫu trước bao phủ mẫu sau và gây dominated-case compile error.
4. `when` được đánh giá ở bước nào?  
   *Đáp án:* Sau khi kiểu pattern đã khớp, trước khi chọn nhánh đó.
5. Java 17 chuẩn có thể dùng cú pháp `instanceof String s` không?  
   *Đáp án:* Có, từ Java 16.
6. Vì sao `switch(action)` trên `sealed Action` có thể không cần `default`?  
   *Đáp án:* Compiler biết toàn bộ subtype được phép và các pattern đã bao phủ chúng.
7. Dùng gì để trả giá trị từ một khối trong switch expression?  
   *Đáp án:* `yield`.

## Checklist

- [ ] Phân biệt **`instanceof` type pattern chuẩn Java 17** và **pattern matching for switch chuẩn Java 21**.
- [ ] Hiểu `case Type variable` vừa kiểm tra kiểu vừa ràng buộc biến đúng kiểu.
- [ ] Dùng `when` cho điều kiện bổ sung và đặt mẫu hẹp trước mẫu rộng.
- [ ] Xử lý `null` tường minh bằng `case null` khi cần; không nhầm với `default`.
- [ ] Dùng `->` để tránh fall-through, `yield` khi nhánh switch expression là một khối.
- [ ] Nắm quy tắc exhaustiveness, đặc biệt với sealed hierarchy.
- [ ] Biết viết phương án Java 17 bằng `if` và `instanceof` nếu môi trường chưa nâng lên JDK 21.

**Kết luận:** Java 21 đưa kiểm tra kiểu trực tiếp vào `switch`, giúp chọn nhánh theo loại đối tượng, kết hợp guard `when`, xử lý `null` và kiểm tra độ bao phủ. Khi dự án vẫn dùng Java 17, hãy dùng `instanceof` type pattern với `if` để đạt hành vi tương đương mà không phụ thuộc preview.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-006">Điều kiện bảo vệ when và thứ tự nhãn mẫu</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện bảo vệ `when` và thứ tự nhãn mẫu — Java 17+

## 1. Bản chất bài học và lưu ý phiên bản

Khi dùng **pattern matching trong `switch`**, ta có thể chọn nhánh dựa trên **kiểu dữ liệu** và thêm **điều kiện bảo vệ** (*guard*) để chỉ chấp nhận mẫu thỏa ràng buộc bổ sung. Cú pháp phổ biến:

~~~java
// Java 21 trở lên: switch pattern matching ổn định.
case String text when text.length() >= 5 -> ...
~~~

Ý nghĩa: trước hết selector phải khớp mẫu `String text`; **chỉ khi đó** Java mới đánh giá `text.length() >= 5`. Nếu guard false, tiếp tục tìm nhãn case sau.

**Phân biệt phiên bản rất quan trọng:**

| JDK | Trạng thái và cú pháp |
|---|---|
| Java 17 | Pattern matching cho `instanceof` **ổn định**; pattern matching cho `switch` là **preview** (JEP 406), guard preview dùng dạng `case String s && condition`, **không có cú pháp `when`** |
| Java 21+ | Pattern matching cho `switch` **ổn định** (JEP 441); viết `case String s when condition -> ...`, không cần preview |

Vì vậy **không thể biên dịch `case ... when ...` bằng `javac --release 17`**. Bài này có hai chương trình hoàn chỉnh:

1. **JDK 17+**: dùng `instanceof` pattern kết hợp `if/else` và `&&`, biểu diễn cùng logic và kiểm chứng được trên Java 17.
2. **JDK 21+**: dùng trực tiếp `switch` với `when`, đúng cú pháp mà đề bài hỏi.

Không sử dụng cú pháp Java 21 trong đoạn mã được ghi là chạy trên JDK 17.

## 2. `when` kiểm tra điều kiện sau khi mẫu khớp

Ví dụ Java 21:

~~~java
static String classify(Object value) {
    return switch (value) {
        case null -> "NULL";
        case String s when s.isBlank() -> "BLANK";
        case String s when s.length() >= 5 -> "LONG_TEXT";
        case String s -> "SHORT_TEXT";
        default -> "OTHER";
    };
}
~~~

Luồng đối với `value = "example"`:

1. Không khớp `case null`.
2. Khớp kiểu `String` ở nhãn có `s.isBlank()`, nhưng guard false.
3. Tiếp tục nhãn kế tiếp: khớp `String` và `s.length() >= 5` true.
4. Chọn `"LONG_TEXT"`; **không xét** các nhãn phía dưới.

Nếu `value = 42`, Java không gọi `s.isBlank()` hay `s.length()`, vì mẫu `String s` không khớp. Biến mẫu `s` thuộc phạm vi của **guard** và **thân nhánh được chọn**, không tồn tại vô điều kiện cho mọi case.

~~~mermaid
flowchart TD
    A["Đánh giá selector một lần"] --> B{"Mẫu nhãn đầu khớp?"}
    B -- "Không" --> F["Thử nhãn tiếp theo"]
    B -- "Có" --> C{"Có when và guard true?"}
    C -- "Không" --> F
    C -- "Có / không có guard" --> D["Chọn nhánh và chạy thân nhánh"]
    F --> B
    D --> E["Rời switch rule/expression"]
~~~

Sơ đồ nêu nguyên tắc chung; với guard false, Java **không thực hiện thân nhánh** dù kiểu mẫu đã khớp.

## 3. `when` phải đi kèm nhãn mẫu phù hợp

Ví dụ đúng trong Java 21:

~~~java
case String text when !text.isBlank() -> "TEXT"
case Integer number when number > 0 -> "POSITIVE_INTEGER"
~~~

Các biểu thức sau `when` phải là điều kiện **`boolean`** (hoặc giá trị `Boolean` có thể được unbox), và được kiểm tra theo luật ngôn ngữ như một biểu thức điều kiện thông thường.

**Không đặt `when` lên một nhãn hằng theo cùng cách:**

~~~java
// Không phải cú pháp guarded pattern Java 21:
// case 10 when someBoolean -> "TEN";
~~~

Nếu cần kiểm tra thêm điều kiện trên giá trị 10, có thể dùng mẫu kiểu kèm guard (khi hợp ngữ cảnh), hoặc tốt hơn là dùng `if` nếu nghiệp vụ chỉ là phép so sánh một số và điều kiện phụ.

Một guard có thể kết hợp nhiều điều kiện:

~~~java
case String text when !text.isBlank()
        && text.length() <= 20 -> "VALID_TEXT"
~~~

Bên trong guard, `text` đã được biết là String **khác null**, nhưng các method trong guard vẫn có thể tạo tác dụng phụ hoặc ném ngoại lệ nếu chúng làm việc khác. Hãy ưu tiên guard ngắn, thuần túy và dễ kiểm thử.

## 4. Thứ tự nhãn mẫu ảnh hưởng đến kết quả

Hai guard có thể **cùng true** cho một giá trị. Nhánh khớp đầu tiên theo thứ tự nhãn được chọn.

~~~java
return switch (value) {
    case String s when s.startsWith("A") -> "STARTS_A";
    case String s when s.length() >= 5 -> "LONG";
    case String s -> "OTHER_STRING";
    default -> "NOT_STRING";
};
~~~

Với `"AppleTree"`, hai guard đầu đều true, nhưng kết quả là `"STARTS_A"` vì nhãn ấy đứng trước. Với `"Mexico"`, guard đầu false, guard thứ hai true, kết quả `"LONG"`.

**Lời khuyên:** sắp các điều kiện **ưu tiên cao hoặc đặc thù hơn** lên trước. Viết bảng quy tắc khi có nhiều guard giao nhau:

| Đầu vào | `startsWith("A")` | `length >= 5` | Nhãn được chọn |
|---|---|---|---|
| `"AppleTree"` | true | true | `STARTS_A` (đầu tiên) |
| `"Alex"` | true | false | `STARTS_A` |
| `"Mexico"` | false | true | `LONG` |
| `"Go"` | false | false | `OTHER_STRING` |
| `42` | Không xét guard String | Không xét | `NOT_STRING` |

Đổi thứ tự hai nhãn có guard có thể **đổi kết quả**, ngay cả khi chương trình vẫn biên dịch hợp lệ.

## 5. Dominance: compiler phát hiện nhãn bị che khuất

Một nhãn kiểu rộng không có guard có thể bao phủ hết nhãn kiểu hẹp đứng sau. Ví dụ `CharSequence` bao gồm `String`, nên thứ tự sau không biên dịch trên Java 21:

~~~java
// case CharSequence cs -> "SEQUENCE";
// case String s -> "STRING"; // Bị dominated.
~~~

Đảo thứ tự thành `case String s` rồi `case CharSequence cs` để giữ cả hai đường. Cùng kiểu mẫu, `case String s -> ...` đặt trước `case String s when s.length() >= 5 -> ...` cũng làm nhãn guarded bị che; hãy đặt nhãn có guard trước nhãn chung.

Giữa **hai guard tùy ý**, compiler thường không thể suy luận mọi quan hệ bao hàm của điều kiện. Hai guard cùng true vẫn biên dịch được và **thứ tự quyết định kết quả** như ở mục 4. Phần về coverage/exhaustiveness của chương phân tích sâu hơn nhãn bị che và trường hợp cần `default`.

## 6. Phân biệt guard false với type mismatch

Giả sử:

~~~java
return switch (value) {
    case Integer n when n < 0 -> "NEGATIVE_INT";
    case Integer n -> "OTHER_INT";
    case Number n -> "OTHER_NUMBER";
    default -> "OTHER";
};
~~~

- `Integer.valueOf(-2)`: khớp Integer, guard `n < 0` true → `NEGATIVE_INT`.
- `Integer.valueOf(0)`: khớp Integer nhưng guard false → thử nhãn Integer kế tiếp → `OTHER_INT`.
- `Double.valueOf(2.5)`: **không khớp** mẫu Integer → khớp `Number` → `OTHER_NUMBER`.
- `"Go"`: không khớp Integer/Number → `OTHER`.

Mẫu `Integer n` đã bảo đảm `n` không null và là `Integer` khi guard được xét, nên `n < 0` có thể unbox để so sánh an toàn trong trường hợp này.

**Đừng nhầm** “guard false” với “selector không đúng kiểu”: cả hai đều dẫn tới việc thử nhãn sau, nhưng việc guard có được đánh giá hay không thì khác.

## 7. `null` cần nhãn riêng hoặc guard trước switch

Với switch pattern matching Java 21, có thể dùng:

~~~java
return switch (value) {
    case null -> "NULL";
    case String s when s.isBlank() -> "BLANK";
    case String s -> "TEXT";
    default -> "OTHER";
};
~~~

Đừng giả định `default` tự xử lý `null` theo mọi hình thức switch. Nếu switch không có nhãn xử lý null phù hợp, selector null có thể gây `NullPointerException`.

Trong Java 17 ổn định, pattern matching cho `switch` chưa là tính năng ổn định; cách dễ hiểu là kiểm tra trước bằng `if (value == null)` rồi mới dùng `instanceof`.

**Chú ý:** một mẫu kiểu `String s` thông thường không khớp null, vì vậy `s.isBlank()` không chạy cho null. Tránh đặt logic xử lý null trong thân nhánh String rồi mong nó được gọi khi selector null.

## 8. Mối liên hệ với flow scoping và `instanceof` của Java 17

Trong Java 17, pattern matching ổn định cho `instanceof` có thể biểu đạt điều kiện tương đương:

~~~java
static String classify17(Object value) {
    if (value == null) return "NULL";
    if (value instanceof String s && s.isBlank()) return "BLANK";
    if (value instanceof String s && s.length() >= 5) return "LONG_TEXT";
    if (value instanceof String s) return "SHORT_TEXT";
    return "OTHER";
}
~~~

Tại `value instanceof String s && s.length() >= 5`, vế phải `&&` chỉ chạy khi mẫu đã khớp. Đây là **flow scoping**, tức compiler chỉ cho dùng `s` tại nơi có thể chứng minh mẫu đã khớp.

Java 17 **không cần** `when` để dạy quy tắc đặt guard sau bước kiểm tra kiểu.

**Hai điểm khác nhau:**

- Chuỗi `if` có thể lặp lại phép `instanceof`; switch Java 21 diễn đạt nhiều nhãn mẫu trên cùng selector.
- Java 21 áp dụng **dominance** cho thứ tự nhãn mẫu trong switch; nếu một pattern không guard rộng đứng trước mẫu con, lỗi có thể được phát hiện ngay lúc biên dịch. Với `if`, điều kiện rộng đứng trước thường là lỗi logic không được compiler cảnh báo.

## 9. `when` khác `if` đặt bên trong case

Một nhãn có guard:

~~~java
case String s when s.length() >= 5 -> "LONG_TEXT"
~~~

chỉ được chọn khi **cả kiểu String và guard** cùng đạt.

Nhưng nếu viết:

~~~java
case String s -> {
    if (s.length() >= 5) {
        yield "LONG_TEXT";
    }
    yield "SHORT_TEXT";
}
~~~

thì **mọi String** đều chọn nhãn `case String s`; các nhãn case phía sau sẽ không có cơ hội xử lý String đó. Guard `when` vì vậy không đơn thuần là cách làm gọn một `if` nằm trong case: guard false có nghĩa là **tiếp tục thử nhãn khác**.

Dùng `when` khi việc không thỏa điều kiện cần chuyển quyền lựa chọn sang case khác. Dùng `if` trong thân case khi bạn đã chủ định chọn nhãn đó và xử lý thêm **bên trong**.

## 10. `switch` expression, statement và tính đầy đủ

Guarded patterns sử dụng được khi viết switch **expression** trả một giá trị hoặc switch **statement** thực hiện hành động, theo cú pháp phù hợp.

Switch expression Java 21 cần có nhánh để bao phủ mọi selector. Với `Object`, cách dễ hiểu là cung cấp `default` và xử lý null theo chính sách:

~~~java
String kind = switch (value) {
    case null -> "NULL";
    case String s when !s.isBlank() -> "TEXT";
    case String s -> "BLANK";
    default -> "OTHER";
};
~~~

Một guard tùy ý không thể được xem là luôn true để thay cho nhánh bao quát, vì `when` có thể false.

**Không viết `case String s when ...` sau `case String s`**, vì nhãn tổng quát không guard sẽ che nhãn có guard.

Nếu nhánh cần nhiều câu lệnh, dùng khối `-> { ... yield result; }` trong switch expression; `yield` cung cấp **giá trị của switch expression**, không thay thế `when`.

## 11. Guard có side effect: thứ tự quan sát được

Với Java 21:

~~~java
static boolean trace(String text) {
    System.out.println("guard:" + text);
    return text.length() > 2;
}

static String choose(Object value) {
    return switch (value) {
        case String s when trace(s) -> "MATCH";
        case null -> "NO_MATCH";
        default -> "NO_MATCH";
    };
}
~~~

- `choose(42)`: **không gọi** `trace`, vì mẫu String không khớp.
- `choose("Go")`: có gọi `trace("Go")`, guard false và đi tới default.
- `choose("Java")`: có gọi `trace("Java")`, guard true và trả MATCH.

Nếu nhiều nhãn có guard đều có side effect, **đổi thứ tự nhãn có thể đổi side effect và kết quả**. Vì vậy, hạn chế gọi method thay đổi trạng thái trong guard; chỉ để lại điều kiện boolean cần thiết.

Nếu guard ném ngoại lệ, đó không phải kết quả false: exception được truyền theo luồng xử lý ngoại lệ. Đừng kỳ vọng switch âm thầm thử nhãn kế tiếp sau một exception.

## 12. Chương trình chạy độc lập trên JDK 17+

File **`PatternGuardOrderDemo17.java`** dùng `instanceof` pattern và `if`; không chứa cú pháp `when`, nên **biên dịch trên Java 17 và các JDK mới hơn**.

~~~java
public final class PatternGuardOrderDemo17 {
    private PatternGuardOrderDemo17() {}

    static String describe(Object value) {
        if (value == null) return "NULL";
        if (value instanceof String s && s.isBlank()) return "BLANK";
        if (value instanceof String s && s.length() >= 5) {
            return "LONG_TEXT:" + s.strip();
        }
        if (value instanceof String s) return "TEXT:" + s;
        if (value instanceof Integer n && n < 0) return "NEGATIVE_INT";
        if (value instanceof Integer n) return "INT:" + n;
        if (value instanceof Number n) {
            return "NUMBER:" + n.getClass().getSimpleName();
        }
        return "OTHER";
    }

    static String priority(Object value) {
        if (value instanceof String s && s.startsWith("A")) {
            return "STARTS_A";
        }
        if (value instanceof String s && s.length() >= 5) return "LONG";
        if (value instanceof String) return "OTHER_STRING";
        return "NOT_STRING";
    }

    static boolean traced(String name, boolean allowed) {
        System.out.println("guard called:" + name);
        return allowed;
    }

    static String trace(Object value) {
        if (value instanceof String s && traced(s, s.length() > 2)) {
            return "MATCH";
        }
        return "NO_MATCH";
    }

    public static void main(String[] args) {
        Object[] inputs = {
            null, "   ", " example ", "Go",
            Integer.valueOf(-2), Integer.valueOf(0),
            Double.valueOf(2.5), Boolean.TRUE
        };
        for (Object value : inputs) {
            System.out.println("describe=" + describe(value));
        }

        System.out.println("priority Alex=" + priority("Alex"));
        System.out.println("priority AppleTree=" + priority("AppleTree"));
        System.out.println("priority Mexico=" + priority("Mexico"));
        System.out.println("priority Go=" + priority("Go"));
        System.out.println("priority null=" + priority(null));

        System.out.println("trace int=" + trace(7));
        System.out.println("trace Go=" + trace("Go"));
        System.out.println("trace Java=" + trace("Java"));
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 PatternGuardOrderDemo17.java
java -cp . PatternGuardOrderDemo17
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\PatternGuardOrderDemo17.java
java -cp . PatternGuardOrderDemo17
~~~

### Exact expected stdout trên JDK 17+

~~~text
describe=NULL
describe=BLANK
describe=LONG_TEXT:example
describe=TEXT:Go
describe=NEGATIVE_INT
describe=INT:0
describe=NUMBER:Double
describe=OTHER
priority Alex=STARTS_A
priority AppleTree=STARTS_A
priority Mexico=LONG
priority Go=OTHER_STRING
priority null=NOT_STRING
trace int=NO_MATCH
guard called:Go
trace Go=NO_MATCH
guard called:Java
trace Java=MATCH
~~~

**Giải thích:** `trace(7)` không gọi guard; `trace("Go")` gọi guard nhưng trả false; `trace("Java")` gọi guard và trả true. Các nhánh `if` theo thứ tự thể hiện logic tương đương các nhãn có `when` trong Java 21.

## 13. Chương trình dùng `when` thật sự trên JDK 21+

File **`PatternWhenDemo21.java`** (JDK 21 trở lên, không bật preview):

~~~java
public final class PatternWhenDemo21 {
    private PatternWhenDemo21() {}

    static String describe(Object value) {
        return switch (value) {
            case null -> "NULL";
            case String s when s.isBlank() -> "BLANK";
            case String s when s.length() >= 5 -> "LONG_TEXT:" + s.strip();
            case String s -> "TEXT:" + s;
            case Integer n when n < 0 -> "NEGATIVE_INT";
            case Integer n -> "INT:" + n;
            case Number n -> "NUMBER:" + n.getClass().getSimpleName();
            default -> "OTHER";
        };
    }

    static String priority(Object value) {
        return switch (value) {
            case null -> "NOT_STRING";
            case String s when s.startsWith("A") -> "STARTS_A";
            case String s when s.length() >= 5 -> "LONG";
            case String s -> "OTHER_STRING";
            default -> "NOT_STRING";
        };
    }

    static boolean traced(String name, boolean allowed) {
        System.out.println("guard called:" + name);
        return allowed;
    }

    static String trace(Object value) {
        return switch (value) {
            case null -> "NO_MATCH";
            case String s when traced(s, s.length() > 2) -> "MATCH";
            default -> "NO_MATCH";
        };
    }

    public static void main(String[] args) {
        Object[] inputs = {
            null, "   ", " example ", "Go",
            Integer.valueOf(-2), Integer.valueOf(0),
            Double.valueOf(2.5), Boolean.TRUE
        };
        for (Object value : inputs) {
            System.out.println("describe=" + describe(value));
        }

        System.out.println("priority Alex=" + priority("Alex"));
        System.out.println("priority AppleTree=" + priority("AppleTree"));
        System.out.println("priority Mexico=" + priority("Mexico"));
        System.out.println("priority Go=" + priority("Go"));
        System.out.println("priority null=" + priority(null));

        System.out.println("trace int=" + trace(7));
        System.out.println("trace Go=" + trace("Go"));
        System.out.println("trace Java=" + trace("Java"));
    }
}
~~~

**Bash (Linux/macOS), JDK 21+:**

~~~bash
javac --release 21 -encoding UTF-8 PatternWhenDemo21.java
java -cp . PatternWhenDemo21
~~~

**PowerShell (Windows), JDK 21+:**

~~~powershell
javac --release 21 -encoding UTF-8 .\PatternWhenDemo21.java
java -cp . PatternWhenDemo21
~~~

**Exact expected stdout:** giống **toàn bộ 18 dòng** của ví dụ JDK 17 ở mục 12, từ `describe=NULL` đến `trace Java=MATCH`. Hai ví dụ áp dụng **cùng thứ tự quy tắc**, chỉ khác cách diễn đạt cú pháp.

**Không làm điều này:** thử biên dịch file `PatternWhenDemo21.java` với `--release 17`; compiler Java 17 không hiểu cú pháp `when` của switch pattern.

## 14. Lỗi thường gặp và cách sửa

| Sai lầm | Hậu quả | Cách phù hợp |
|---|---|---|
| Viết `case String s when ...` và cho là Java 17 ổn định | Java 17 không hỗ trợ cú pháp đó | JDK 21+ hoặc `if` + `instanceof` Java 17 |
| Đặt nhãn `String s` không guard trước nhãn `String s when ...` | Nhãn sau bị dominated, lỗi compile | Nhãn guarded trước, nhãn chung sau |
| Đặt `CharSequence cs` trước `String s` | Nhãn String phía sau bị dominated | Mẫu cụ thể trước, mẫu rộng sau |
| Hai guard giao nhau nhưng cho rằng thứ tự không quan trọng | Kết quả đổi theo nhãn đầu tiên đúng | Đặt ưu tiên rõ ràng, test vùng giao |
| Nghĩ guard chạy cả khi mẫu không khớp | Dễ suy sai về NPE/side effect | Guard chỉ chạy sau khi mẫu khớp |
| Guard false nhưng nghĩ switch kết thúc | Bỏ mất cơ hội xét nhãn sau | Guard false → tiếp tục tìm nhãn phù hợp |
| Đặt `when` sau nhãn hằng giống guarded pattern | Cú pháp không hợp lệ | Dùng pattern hoặc `if` |
| Dùng `default` để mong nhận `null` không cần kiểm tra | Có thể NPE với selector null | Thêm `case null` trên JDK 21, hoặc null guard |
| Chỉ có các case có guard, không có nhãn bao quát | Switch expression có thể thiếu kết quả | Thêm case tổng quát/`default` |
| Đưa side effect vào guard | Thứ tự nhãn thay đổi hành vi | Dùng điều kiện thuần, tách side effect |
| Nghĩ `when` tương đương `if` bên trong nhánh đã chọn | Guard false thử case khác; if bên trong không | Chọn đúng ngữ nghĩa |
| Trộn cú pháp `case:` và `case ->` tùy tiện | Lỗi compile | Chọn một kiểu nhãn nhất quán |

## 15. Bài tập có đáp án

### Bài A — dự đoán ưu tiên guard

~~~java
// JDK 21+
String category = switch ("Apple") {
    case String s when s.startsWith("A") -> "A";
    case String s when s.length() >= 5 -> "LONG";
    default -> "OTHER";
};
System.out.println(category);
~~~

**Đáp án:** `A`. `"Apple"` thỏa cả hai điều kiện, nhưng nhãn đứng trước được chọn.

### Bài B — tìm lỗi dominance

~~~java
// JDK 21+, KHÔNG BIÊN DỊCH:
// switch (value) {
//     case Number n -> "NUMBER";
//     case Integer i -> "INTEGER";
//     default -> "OTHER";
// }
~~~

**Đáp án:** mẫu `Number n` đứng trước đã bao phủ mọi `Integer`; phải đặt `case Integer i` trước `case Number n`.

### Bài C — guard false phải làm gì?

~~~java
// JDK 21+
String result = switch ("Go") {
    case String s when s.length() >= 5 -> "LONG";
    case String s -> "SHORT";
    default -> "OTHER";
};
System.out.println(result);
~~~

**Đáp án:** `SHORT`. Mẫu String khớp ở case đầu nhưng guard false, nên Java xét tiếp case String tổng quát.

### Bài D — viết tương đương Java 17

Muốn: String blank → BLANK; String không blank → TEXT; kiểu khác → OTHER.

**Đáp án:**

~~~java
static String categorize(Object value) {
    if (value instanceof String s && s.isBlank()) return "BLANK";
    if (value instanceof String) return "TEXT";
    return "OTHER";
}
~~~

Không cần pattern switch preview trên Java 17.

### Bài E — null và guard

~~~java
// Java 21+
String label = switch ((Object) null) {
    case null -> "NULL";
    case String s when s.isBlank() -> "BLANK";
    default -> "OTHER";
};
System.out.println(label);
~~~

**Đáp án:** `NULL`. Nhãn `case null` xử lý null; guard String không được gọi.

## 16. Checklist và kết luận

- [ ] Biết `when` là guard của nhãn pattern switch ổn định từ JDK 21, **không phải cú pháp Java 17**.
- [ ] Biết Java 17 có thể dùng `instanceof` pattern kết hợp `&&` và `if` để diễn đạt cùng điều kiện.
- [ ] Hiểu guard chỉ chạy sau khi mẫu kiểu khớp; biến mẫu dùng được trong guard và nhánh.
- [ ] Hiểu guard false dẫn đến xét nhãn sau, không chạy thân nhánh hiện tại.
- [ ] Biết các nhãn có guard có thể giao nhau và nhãn khớp đầu tiên được chọn.
- [ ] Nhận diện dominance: nhãn rộng không guard trước nhãn hẹp có thể làm code không compile.
- [ ] Đặt nhãn mẫu cụ thể và guarded trước nhãn bao quát không guard.
- [ ] Xử lý null theo cách phù hợp phiên bản; không mặc định default nhận null.
- [ ] Hiểu `when` khác `if` đặt bên trong một case đã được chọn.
- [ ] Bảo đảm switch expression có nhánh bao phủ dữ liệu và kiểu kết quả phù hợp.
- [ ] Tránh side effect trong guard, test các giá trị sát ngưỡng và vùng giao nhau của điều kiện.
- [ ] Chạy `PatternGuardOrderDemo17.java` bằng JDK 17+ và `PatternWhenDemo21.java` bằng JDK 21+ rồi đối chiếu stdout.

**Kết luận:** Guard `when` cho phép bổ sung điều kiện sau một mẫu kiểu trong switch Java 21+. Khi mẫu khớp nhưng guard false, Java thử các nhãn tiếp theo; bởi vậy **thứ tự nhãn mẫu là một phần của ý nghĩa chương trình**. Mẫu tổng quát không guard có thể *dominate* nhãn hẹp hoặc guarded đặt sau và bị compiler từ chối. Trên Java 17, dùng `instanceof` pattern với `if`/`&&` để có cùng logic mà không cần preview.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-007">Mẫu bao phủ, nhãn không thể được chọn và yêu cầu đầy đủ</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu bao phủ, nhãn không thể được chọn và yêu cầu đầy đủ trong Java

> **Java core — language-basics / Mẫu kiểm tra kiểu**  
> **Mục tiêu:** Hiểu quan hệ giữa các type pattern, vì sao nhãn `case` phía sau có thể bị che khuất (*dominated*), và cách một `switch` sử dụng pattern bao phủ mọi giá trị có thể có (*exhaustive*).  
> **Phiên bản:** `instanceof T x` là tính năng chính thức ở Java 17. **Pattern matching for `switch` còn là preview ở JDK 17 (JEP 406)**; ví dụ chương trình bên dưới sử dụng đúng cờ preview. Từ Java 21, pattern matching for switch đã thành tính năng chính thức.

## 1. Phân biệt ba khái niệm

| Khái niệm | Câu hỏi cần trả lời | Minh họa |
| --- | --- | --- |
| Pattern khớp | Giá trị runtime có thuộc kiểu được thử không? | Một `String` khớp `String` và `CharSequence` |
| Nhãn bị che khuất (*dominated*) | Nhãn trước có bắt hết các giá trị mà nhãn sau định bắt không? | `case CharSequence` trước `case String` |
| Tính đầy đủ (*exhaustiveness*) | Mọi giá trị selector hợp lệ có đường xử lý không? | `case String` và `default` trên `Object` |

Với nhiều nhãn `case`, thứ tự **có thể rất quan trọng**. Không giống một chuỗi `if` tự do để kiểm tra cùng giá trị nhiều lần, pattern switch được trình biên dịch kiểm tra để tránh nhãn không thể được chọn.

Một `switch expression` phải có nhánh phù hợp cho tất cả các giá trị selector mà Java yêu cầu bao phủ. Nếu thiếu, chương trình lỗi ở **thời điểm biên dịch**, không phải đợi người dùng nhập một giá trị chưa xử lý.

## 2. Nhãn bị che khuất: kiểu rộng đặt trước kiểu hẹp

`String` thực thi giao diện `CharSequence`. Vì vậy mọi đối tượng `String` đều khớp `CharSequence`, nhưng một `StringBuilder` cũng khớp `CharSequence` mà không phải `String`.

~~~java
Object value = "Java";
System.out.println(value instanceof CharSequence); // true
System.out.println(value instanceof String);       // true
~~~

Trong một pattern switch, cách sắp xếp sau **không hợp lệ**:

~~~java
// KHÔNG BIÊN DỊCH (Java 17 preview / Java 21+):
// return switch (value) {
//     case CharSequence seq -> "SEQUENCE";
//     case String text -> "TEXT"; // Bị case CharSequence che khuất.
//     default -> "OTHER";
// };
~~~

Khi đầu vào là `String`, nhánh `CharSequence` ở trước đã bắt được nó. Vì thế `case String` phía sau **không thể được chọn** (*dominated case label*).

**Sửa bằng thứ tự cụ thể → tổng quát:**

~~~java
static String label(Object value) {
    return switch (value) {
        case String text -> "STRING:" + text.length();
        case CharSequence seq -> "SEQUENCE:" + seq.length();
        default -> "OTHER";
    };
}
~~~

Với đầu vào `"Java"`, kết quả `"STRING:4"`; với `new StringBuilder("abc")`, kết quả `"SEQUENCE:3"`; với `Integer.valueOf(7)`, kết quả `"OTHER"`.

Thứ tự từ kiểu con tới kiểu cha chỉ là cách nhớ thường dùng. Điều kiện cốt lõi là **một nhãn trước không được bao phủ hoàn toàn một nhãn sau**. Nếu cần kết hợp điều kiện phức tạp, phải xét cả kiểu lẫn điều kiện của pattern theo phiên bản Java được sử dụng.

## 3. Nhãn bao phủ toàn bộ và `default`

Trên selector kiểu `Object`, nhãn `case Object any` là một *total type pattern*: nó bao phủ mọi kiểu đối tượng có thể dùng làm giá trị của selector này.

~~~java
static String broad(Object value) {
    return switch (value) {
        case String s -> "STRING";
        case Object any -> "OBJECT";
    };
}
~~~

Không cần thêm `default` vì nhãn `Object` ở cuối đã bao phủ những loại còn lại. Không đặt `case Object any` **trước** `case String s`: như vậy String bị che khuất.

Đồng thời, **không** đặt cả một total pattern và `default` trong cùng switch: chúng là hai nhãn bắt phần còn lại (*match-all*) và xung đột trong quy tắc switch.

~~~java
// KHÔNG BIÊN DỊCH:
// return switch (value) {
//     case Object any -> "ANY";
//     default -> "OTHER"; // Hai nhãn match-all.
// };
~~~

Cách dễ hiểu hơn cho người mới là dùng `default` khi không cần ràng buộc biến:

~~~java
static String fallback(Object value) {
    return switch (value) {
        case String s -> "STRING:" + s;
        case Integer n -> "INT:" + n;
        default -> "OTHER";
    };
}
~~~

**Chú ý null và phiên bản:** Pattern switch thay đổi chi tiết xử lý `null` qua các bản preview. Để chương trình rõ nghĩa trên Java 17 và 21, nếu muốn phân loại null thành một kết quả riêng, hãy viết `case null -> ...` tường minh (xem mục 6). `default` thông thường **không nên được hiểu** là nhãn riêng cho null.

## 4. Tính đầy đủ của `switch expression`

Ví dụ không bao phủ toàn bộ selector `Object`:

~~~java
// KHÔNG BIÊN DỊCH: thiếu đường cho Integer, Double, v.v.
// String result = switch (value) {
//     case String s -> "TEXT";
//     case Integer i -> "INTEGER";
// };
~~~

Dù code chỉ dự kiến truyền String và Integer, kiểu tĩnh của `value` vẫn là `Object`. Trình biên dịch phải xét các loại có thể xuất hiện khác nữa. Có thể sửa bằng `default` hoặc một total pattern phù hợp:

~~~java
String result = switch (value) {
    case String s -> "TEXT";
    case Integer i -> "INTEGER";
    default -> "OTHER";
};
~~~

Bao phủ đầy đủ **không có nghĩa tất cả nhánh đều thực thi**: tại một lần đánh giá, switch chọn một nhánh phù hợp. Cũng không có nghĩa nhánh nào cũng trả giá trị thành công — một nhánh có thể chủ động `throw`.

Với switch expression dạng `case ... ->`, các nhánh block cần `yield expression;` để cung cấp kết quả khi hoàn thành bình thường; dùng `throw` khi trường hợp đó phải bị từ chối.

## 5. Bao phủ toàn bộ phân cấp `sealed` mà không cần `default`

Java 17 đã hỗ trợ `sealed class/interface` chính thức. Khi kiểu selector là sealed interface, compiler biết các kiểu triển khai trực tiếp được phép và có thể kiểm tra các pattern đã bao phủ hết hay chưa.

~~~java
sealed interface Job permits PrintJob, SaveJob {}
record PrintJob(String message) implements Job {}
record SaveJob(int id) implements Job {}

static String describe(Job job) {
    return switch (job) {
        case PrintJob p -> "PRINT:" + p.message();
        case SaveJob s -> "SAVE:" + s.id();
    };
}
~~~

`Job` chỉ có hai kiểu triển khai trực tiếp được cho phép trong ví dụ; cả hai nhãn `case` đã có nên switch expression **exhaustive mà không cần `default`**.

Nếu thêm một kiểu triển khai được phép, chẳng hạn `SendJob`, rồi biên dịch lại, trình biên dịch sẽ yêu cầu cập nhật phần bao phủ hoặc bổ sung nhãn phù hợp. Đây là lợi ích bảo trì đáng kể so với việc đặt một `default` che giấu mọi trường hợp mới.

| Kiểu selector | Ví dụ bao phủ đầy đủ |
| --- | --- |
| `Object` | Các nhãn cụ thể rồi `default` hoặc total pattern phù hợp |
| `String` | Các nhãn chuỗi phù hợp, cùng `default` |
| `enum` | Liệt kê đủ mọi hằng enum, hoặc dùng `default` |
| Sealed interface | Pattern cho đủ các nhánh thuộc phân cấp sealed, hoặc dùng nhãn bao phủ thích hợp |

Đừng nhầm **bao phủ kiểu** với **kiểm tra toàn vẹn dữ liệu**: trường `message` vẫn có thể rỗng/null dù đối tượng là `PrintJob`. Các quy tắc nội dung phải kiểm tra thêm.

## 6. `null` là trường hợp phải thiết kế rõ

Với `instanceof` thông thường, null cho false:

~~~java
Object value = null;
boolean result = value instanceof String; // false
~~~

Nhưng `switch` với pattern không nên được xem như một chuỗi `instanceof` độc lập. Trong Java 17 preview, có thể đưa `null` thành một nhãn tường minh:

~~~java
static String nullable(Object value) {
    return switch (value) {
        case null -> "NULL";
        case String s -> "STRING:" + s.toUpperCase(java.util.Locale.ROOT);
        default -> "OTHER";
    };
}
~~~

`nullable(null)` cho `"NULL"`; `nullable("ok")` cho `"STRING:OK"`; một `Integer` rơi vào `"OTHER"`. Trên Java 17 với switch pattern preview, **không có nhãn null hoặc nhãn tổng quát xử lý null**, selector null thường dẫn đến `NullPointerException`. Do đó hãy kiểm thử null riêng, không giả định default là cách diễn đạt chính xác yêu cầu xử lý null.

Nên viết `case null` rõ ràng nếu null là giá trị đầu vào được hỗ trợ; nếu null là lỗi nghiệp vụ, có thể kiểm tra và ném ngoại lệ có thông báo ở trước switch.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **PatternCoverageDemo.java**:

~~~java
import java.util.Locale;

public final class PatternCoverageDemo {
    sealed interface Job permits PrintJob, SaveJob {}
    record PrintJob(String message) implements Job {}
    record SaveJob(int id) implements Job {}

    private static String label(Object value) {
        return switch (value) {
            case String s -> "STRING:" + s.length();
            case CharSequence seq -> "SEQUENCE:" + seq.length();
            default -> "OTHER";
        };
    }

    private static String broad(Object value) {
        return switch (value) {
            case String s -> "STRING";
            case Object any -> "OBJECT";
        };
    }

    private static String fallback(Object value) {
        return switch (value) {
            case String s -> "TEXT";
            case Integer n -> "INTEGER";
            default -> "OTHER";
        };
    }

    private static String describe(Job job) {
        return switch (job) {
            case PrintJob p -> "PRINT:" + p.message();
            case SaveJob s -> "SAVE:" + s.id();
        };
    }

    private static String nullable(Object value) {
        return switch (value) {
            case null -> "NULL";
            case String s -> "STRING:" + s.toUpperCase(Locale.ROOT);
            default -> "OTHER";
        };
    }

    public static void main(String[] args) {
        System.out.println("labelText=" + label("Java"));
        System.out.println("labelBuilder="
                + label(new StringBuilder("abc")));
        System.out.println("labelInteger=" + label(Integer.valueOf(7)));

        System.out.println("broadString=" + broad("x"));
        System.out.println("broadObject=" + broad(new Object()));

        System.out.println("fallbackText=" + fallback("a"));
        System.out.println("fallbackNumber=" + fallback(3));
        System.out.println("fallbackOther=" + fallback(2.5));

        System.out.println("sealedPrint="
                + describe(new PrintJob("HELLO")));
        System.out.println("sealedSave=" + describe(new SaveJob(42)));

        System.out.println("nullableNull=" + nullable(null));
        System.out.println("nullableText=" + nullable("ok"));
        System.out.println("nullableOther=" + nullable(Integer.valueOf(9)));

        Object missing = null;
        System.out.println("instanceofNull="
                + (missing instanceof String));
    }
}
~~~

**Biên dịch/chạy trên JDK 17** (preview bắt buộc):

~~~bash
javac --enable-preview --release 17 -encoding UTF-8 PatternCoverageDemo.java
java --enable-preview PatternCoverageDemo
~~~

Trình biên dịch JDK 17 có thể in thông báo về preview ra **stderr**, không thuộc stdout của chương trình. Nếu dùng **JDK 21+**, cú pháp trong file này là tính năng chính thức, có thể chạy bằng lệnh thông thường:

~~~bash
javac -encoding UTF-8 PatternCoverageDemo.java
java PatternCoverageDemo
~~~

**Expected stdout chính xác:**

~~~text
labelText=STRING:4
labelBuilder=SEQUENCE:3
labelInteger=OTHER
broadString=STRING
broadObject=OBJECT
fallbackText=TEXT
fallbackNumber=INTEGER
fallbackOther=OTHER
sealedPrint=PRINT:HELLO
sealedSave=SAVE:42
nullableNull=NULL
nullableText=STRING:OK
nullableOther=OTHER
instanceofNull=false
~~~

Chương trình minh họa ba ý: nhãn cụ thể phải đặt trước nhãn rộng hơn; tổng pattern hoặc nhãn mặc định bao phủ trường hợp còn lại; sealed interface được xử lý đủ hai nhánh mà không cần default. Với `null`, chương trình có nhãn tường minh thay vì phụ thuộc vào mặc định.

## 8. Lỗi thường gặp và bài tập

| Hiện tượng | Nguyên nhân / cách sửa |
| --- | --- |
| `case CharSequence` trước `case String` | `case String` bị che khuất; chuyển nhãn String lên trước |
| `case Object` trước mọi nhãn hẹp | Các nhãn hẹp phía sau bị che khuất; đặt total pattern cuối |
| Cả total pattern và `default` | Hai nhãn match-all; chỉ giữ một |
| Switch expression thiếu xử lý đầu vào | Không exhaustive; thêm `default` hoặc pattern bao phủ hợp lệ |
| Sealed type có subtype mới mà switch chưa cập nhật | Mở rộng nhánh tương ứng và biên dịch lại |
| Cho rằng null tự vào `default` như các giá trị khác | Xử lý `case null` hoặc kiểm tra trước switch |
| Viết pattern switch trên JDK 17 không bật preview | Dùng `javac --enable-preview --release 17` và `java --enable-preview` |

**Bài 1.** Đổi thứ tự `case String` và `case CharSequence` trong `label`; dự đoán lỗi biên dịch và giải thích bằng quan hệ kiểu.

**Bài 2.** Bỏ `default` khỏi `fallback`. Trình biên dịch báo lỗi vì giá trị thuộc kiểu nào chưa được bao phủ?

**Bài 3.** Trong `broad`, đưa `case Object` lên trước `case String`. Nhãn nào bị che khuất? Có thể sửa chỉ bằng thêm `break` không?

**Bài 4.** Thêm kiểu mới được phép vào sealed interface `Job` và cập nhật switch expression `describe` cho exhaustive.

**Bài 5.** Kiểm thử `null` với `instanceof String` và `nullable(null)`; giải thích vì sao một phép cho false còn phép còn lại vào nhánh `case null`.

### Ghi nhớ

**Dominance**: pattern rộng đặt trước có thể làm nhãn hẹp không thể chọn, gây lỗi biên dịch. **Exhaustiveness**: switch expression phải bao phủ tất cả khả năng; có thể dùng `default`, total pattern hoặc đủ các nhánh của phân cấp `sealed`. **Null** phải được thiết kế rõ. Khi học với **JDK 17**, pattern switch cần bật **preview**; từ Java 21 đây là tính năng chính thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-008">Nhận diện record pattern để tách dữ liệu của record</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện record pattern để tách dữ liệu của `record` — Java 17+/21

**Record pattern** (*mẫu phân rã record*) cho phép kiểm tra một đối tượng có đúng kiểu `record` và **trích các thành phần của record thành biến** ngay trong phép kiểm tra. Đây là tính năng **chuẩn từ Java 21 (JEP 440)**. Trong Java 17, `record` đã là tính năng chuẩn, nhưng **cú pháp record pattern chưa phải tính năng chuẩn**.

~~~java
record Point(int x, int y) {}

Object value = new Point(2, 3);
if (value instanceof Point(int x, int y)) { // JDK 21+
    System.out.println(x + y);              // 5
}
~~~

`Point(int x, int y)` là **record pattern**, khác với `new Point(2, 3)` là biểu thức **tạo đối tượng**. Khi pattern khớp, nó lấy hai thành phần của record và ràng buộc vào biến `x`, `y` mà không cần ép kiểu rồi gọi riêng `point.x()` và `point.y()`.

## 1. Phân biệt record, type pattern và record pattern

| Cấu trúc | Ý nghĩa | Phiên bản chuẩn |
| --- | --- | --- |
| `record Point(int x, int y) {}` | Khai báo lớp record có hai component | Java 16+ |
| `obj instanceof Point p` | Kiểm tra kiểu, ràng buộc cả đối tượng vào `p` | Java 16+ |
| `obj instanceof Point(int x, int y)` | Kiểm tra kiểu **và phân rã hai component** | Java 21+ |
| `case Point(int x, int y) -> ...` | Dùng record pattern trong switch | Java 21+ |

Với **type pattern**, ta có biến `p` là cả record và đọc component qua `p.x()`, `p.y()`. Với **record pattern**, biến `x` và `y` đại diện trực tiếp cho từng component. Record pattern **không phải phép destructuring assignment** độc lập: nó cần xuất hiện trong ngữ cảnh hỗ trợ pattern, chẳng hạn `instanceof` hoặc `switch`.

**Quy tắc quan trọng:** số lượng, thứ tự và kiểu các nested pattern phải tương ứng với danh sách component của record. `Point(int x, int y)` dùng hai component theo **thứ tự khai báo**, không theo tên biến bạn đặt.

## 2. Chương trình chạy với JDK 21+

Tạo file UTF-8 tên **`RecordPattern21Demo.java`**:

~~~java
public class RecordPattern21Demo {
    record Point(int x, int y) {}
    record Segment(Point start, Point end) {}
    record Person(String name, int age) {}

    static String describe(Object value) {
        return switch (value) {
            case null -> "null";
            case Point(int x, int y) -> "point=" + x + "," + y;
            case Segment(Point(int sx, int sy), Point(int ex, int ey)) ->
                    "segment=" + sx + "," + sy + "->" + ex + "," + ey;
            case Person(String name, int age) when age >= 18 ->
                    "adult=" + name;
            case Person(String name, int age) ->
                    "minor=" + name;
            default -> "other";
        };
    }

    static int sum(Object value) {
        if (value instanceof Point(int x, int y)) {
            return x + y;
        }
        return -1;
    }

    static int squaredDistance(Object value) {
        if (value instanceof Segment(
                Point(int x1, int y1),
                Point(int x2, int y2))) {
            int dx = x2 - x1;
            int dy = y2 - y1;
            return dx * dx + dy * dy;
        }
        return -1;
    }

    public static void main(String[] args) {
        Object[] values = {
            null,
            new Point(2, 3),
            new Segment(new Point(0, 0), new Point(2, 3)),
            new Person("Mai", 20),
            new Person("Nam", 15),
            new Segment(null, new Point(1, 1)),
            42
        };

        for (Object value : values) {
            System.out.println(describe(value));
        }

        System.out.println("sum=" + sum(new Point(2, 3)));
        System.out.println("notPoint=" + sum("hello"));
        System.out.println("distance="
                + squaredDistance(new Segment(
                        new Point(0, 0), new Point(2, 3))));
        System.out.println("missingStart="
                + squaredDistance(new Segment(null, new Point(1, 1))));
    }
}
~~~

Dùng **JDK 21 trở lên** trong PowerShell (Windows) hoặc Bash (Linux/macOS):

~~~bash
java -version
javac -version
javac -encoding UTF-8 RecordPattern21Demo.java
java RecordPattern21Demo
~~~

**Exact expected stdout** của riêng lệnh `java RecordPattern21Demo`:

~~~text
null
point=2,3
segment=0,0->2,3
adult=Mai
minor=Nam
other
other
sum=5
notPoint=-1
distance=13
missingStart=-1
~~~

`java -version` và `javac -version` in phiên bản phụ thuộc JDK cài trên máy. Với JDK 21, ví dụ trên **không cần `--enable-preview`**.

## 3. Nested record pattern: phân rã nhiều tầng

`Segment` chứa hai component kiểu `Point`, nên có thể viết pattern lồng:

~~~java
if (value instanceof Segment(
        Point(int x1, int y1),
        Point(int x2, int y2))) {
    System.out.println(x1 + "," + y1);
    System.out.println(x2 + "," + y2);
}
~~~

Khi thành công, `value` phải là `Segment` và cả `start` lẫn `end` phải khớp hai pattern `Point(...)`. Các component được trích thành **bốn biến `int`**.

Nếu `Segment` chứa `start == null`, pattern `Point(int x1, int y1)` cho component đó **không khớp**, nên toàn bộ nested pattern thất bại. Đó là lý do demo in `missingStart=-1` thay vì phát sinh lỗi do gọi `start.x()` trên null.

Có thể đọc kết quả phân rã theo sơ đồ:

~~~mermaid
flowchart TD
    A["value"] --> B{"Segment?"}
    B -- Không --> F["Pattern không khớp"]
    B -- Có --> C{"start khớp Point?"}
    C -- Không --> F
    C -- Có --> D{"end khớp Point?"}
    D -- Không --> F
    D -- Có --> E["Ràng buộc x1, y1, x2, y2"]
~~~

Với component là kiểu tham chiếu, Java có thể dùng các pattern thích hợp như `String name` để kiểm tra/ràng buộc thêm. Record pattern được kết hợp từ nhiều pattern con chứ không chỉ cho phép primitive `int`.

## 4. `null`, `when` và thứ tự pattern trong `switch`

Pattern `Point(int x, int y)` không khớp với selector `null`. Trong **switch Java 21**, ta có thể xử lý rõ ràng:

~~~java
String label = switch (value) {
    case null -> "missing";
    case Point(int x, int y) when x == 0 && y == 0 -> "origin";
    case Point(int x, int y) -> "point";
    default -> "other";
};
~~~

Ở đây:

- `case null` xử lý riêng selector null; **`default` không tự thay thế `case null`**.
- `when` bổ sung điều kiện **sau khi pattern khớp**.
- Nhánh origin phải đặt **trước** nhánh `Point(...)` chung. Đảo thứ tự có thể khiến nhánh origin bị một pattern rộng hơn chiếm hết (*dominated case*) và không biên dịch.
- Dạng `case ... ->` không rơi tiếp xuống nhãn khác.

**Record pattern không giải quyết mọi trường hợp `null`:** selector null cần `case null` hoặc kiểm tra từ trước; component null có thể làm nested pattern không khớp, tùy pattern bên trong. Việc phân rã component dùng các accessor của record; tránh tạo accessor có tác dụng phụ hoặc ném ngoại lệ nếu muốn phép kiểm tra dễ dự đoán.

## 5. Cách tương thích JDK 17 — dùng record accessor và type pattern

Để chạy trên **JDK 17** mà không dùng record pattern, kiểm tra kiểu bằng `instanceof Point p`, rồi đọc component bằng `p.x()` và `p.y()`.

Tạo tệp UTF-8 **`RecordPattern17Compat.java`**:

~~~java
public class RecordPattern17Compat {
    record Point(int x, int y) {}
    record Segment(Point start, Point end) {}
    record Person(String name, int age) {}

    static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Point point) {
            return "point=" + point.x() + "," + point.y();
        }
        if (value instanceof Segment segment
                && segment.start() != null
                && segment.end() != null) {
            Point start = segment.start();
            Point end = segment.end();
            return "segment=" + start.x() + "," + start.y()
                    + "->" + end.x() + "," + end.y();
        }
        if (value instanceof Person person) {
            return (person.age() >= 18 ? "adult=" : "minor=")
                    + person.name();
        }
        return "other";
    }

    static int sum(Object value) {
        if (value instanceof Point point) {
            return point.x() + point.y();
        }
        return -1;
    }

    static int squaredDistance(Object value) {
        if (value instanceof Segment segment
                && segment.start() != null
                && segment.end() != null) {
            int dx = segment.end().x() - segment.start().x();
            int dy = segment.end().y() - segment.start().y();
            return dx * dx + dy * dy;
        }
        return -1;
    }

    public static void main(String[] args) {
        Object[] values = {
            null,
            new Point(2, 3),
            new Segment(new Point(0, 0), new Point(2, 3)),
            new Person("Mai", 20),
            new Person("Nam", 15),
            new Segment(null, new Point(1, 1)),
            42
        };

        for (Object value : values) {
            System.out.println(describe(value));
        }
        System.out.println("sum=" + sum(new Point(2, 3)));
        System.out.println("notPoint=" + sum("hello"));
        System.out.println("distance="
                + squaredDistance(new Segment(
                        new Point(0, 0), new Point(2, 3))));
        System.out.println("missingStart="
                + squaredDistance(new Segment(null, new Point(1, 1))));
    }
}
~~~

Chạy với **JDK 17+**:

~~~bash
javac -encoding UTF-8 RecordPattern17Compat.java
java RecordPattern17Compat
~~~

**Exact expected stdout** của lệnh `java RecordPattern17Compat` **giống hệt** khối stdout trong mục 2. Bản Java 17 truy cập accessor tường minh và kiểm tra null cho nested record, nhưng không có cú pháp phân rã trực tiếp của Java 21.

## 6. Giới hạn và lỗi thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Có `record` ở Java 17 thì đã có `record pattern`.” | Record chuẩn từ Java 16; record pattern chuẩn từ Java 21 |
| “`new Point(2,3)` giống `Point(int x,int y)`.” | Một bên tạo record, một bên là pattern để khớp và phân rã |
| “Record pattern thay đổi hoặc sao chép record.” | Chỉ kiểm tra và ràng buộc component, không tạo record mới |
| “`Point(int y,int x)` tự lấy đúng tên component theo tên biến.” | Pattern ánh xạ **theo vị trí**, không theo tên biến |
| “Nested pattern tự bỏ qua component null.” | Pattern con phải khớp; null có thể khiến cả pattern thất bại |
| “Đặt `case Point(...)` trước `case Point(...) when ...`.” | Nhánh tổng quát có thể làm nhánh đặc biệt bị dominance |
| “`default` luôn bắt selector null.” | Cần xử lý null riêng khi nghiệp vụ yêu cầu |
| “JDK 17 biên dịch file có `case Point(int x,int y)`.” | Không ở chế độ Java 17 chuẩn |
| “Biến `x`, `y` luôn dùng được sau phép kiểm tra thất bại.” | Biến chỉ hợp lệ trong **flow scope** đã chứng minh pattern khớp |

Các component record là thành phần của mô hình dữ liệu; đừng lạm dụng record pattern quá dài nhiều tầng nếu việc đặt biến trung gian hoặc gọi accessor sẽ dễ đọc hơn.

## 7. Bài tập tự kiểm tra

1. `Object p = new Point(4, 5);`, nếu khớp `Point(int x, int y)` thì `x + y` bằng gì?  
   *Đáp án:* `9`.
2. Record pattern kiểm tra kiểu runtime hay chỉ tên biến?  
   *Đáp án:* Kiểm tra kiểu rồi phân rã component theo thứ tự khai báo.
3. Với `Segment(null, new Point(1,1))`, nested pattern cần cả hai `Point(...)` có khớp không?  
   *Đáp án:* Không.
4. Trong switch Java 21, có thể dùng `case Point(int x,int y) when x == 0` không?  
   *Đáp án:* Có, đặt trước nhánh Point tổng quát.
5. `record Point` có chạy được JDK 17 không? Record pattern có chuẩn ở JDK 17 không?  
   *Đáp án:* Có record, nhưng chưa có record pattern chuẩn.
6. Muốn lấy component Point với JDK 17, làm thế nào?  
   *Đáp án:* `if (obj instanceof Point p)` rồi `p.x()` và `p.y()`.

## Checklist

- [ ] Phân biệt khai báo `record` với cú pháp `record pattern`.
- [ ] Nhớ record pattern chuẩn từ **Java 21**, còn record/type pattern `instanceof` đã có trên Java 17.
- [ ] Đọc đúng component theo thứ tự và phạm vi biến sau khi pattern khớp.
- [ ] Hiểu nested record pattern và tình huống component null.
- [ ] Dùng `when` và `case null` có chủ đích trong switch Java 21.
- [ ] Biết cách thay bằng `instanceof` + accessor nếu môi trường còn dùng JDK 17.

**Kết luận:** Record pattern giúp tách các component của record **ngay trong bước kiểm tra kiểu**, kể cả khi các record lồng nhau. Dùng JDK 21+ để chạy cú pháp này; với dự án JDK 17, vẫn có thể làm cùng công việc thông qua type pattern `instanceof` và các phương thức accessor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-patterns-009">Phân biệt tính năng chính thức với cú pháp preview theo phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tính năng chính thức với cú pháp preview theo phiên bản — Java 17+

## 1. Mục tiêu và quy tắc quan trọng nhất

Trong Java, **tính năng chính thức/ổn định** (*final, permanent language feature*) là cú pháp đã được đưa vào đặc tả ngôn ngữ của phiên bản phát hành. Nó dùng được khi biên dịch đúng phiên bản mà **không cần** `--enable-preview`.

**Tính năng preview** là chức năng ngôn ngữ đã có trong một bản JDK để lập trình viên thử nghiệm, nhưng **chưa trở thành cú pháp ổn định** ở phiên bản ấy. Khi biên dịch và chạy code preview, phải bật tùy chọn phù hợp với **JDK đang dùng**; cú pháp/semantics của preview còn có thể thay đổi trước khi được ổn định.

Ví dụ liên quan đến **mẫu kiểm tra kiểu** (*type patterns*):

~~~java
// Ổn định trong Java 17: pattern matching cho instanceof.
if (input instanceof String text) {
    System.out.println(text.length());
}
~~~

Nhưng:

~~~java
// Java 21 trở lên: pattern matching cho switch đã ổn định.
switch (input) {
    case String text when text.length() >= 5 -> System.out.println("LONG");
    default -> System.out.println("OTHER");
}
~~~

**Đoạn thứ hai KHÔNG biên dịch trong Java 17 ổn định**. Java 17 từng có pattern matching cho switch ở dạng **preview**, nhưng guard của preview Java 17 dùng cú pháp khác: `case String s && condition`, **không phải `when`**.

**Nguyên tắc:** Khi gặp cú pháp mới trên mạng, xác nhận đồng thời **(1) phiên bản JDK**, **(2) trạng thái final/preview tại phiên bản đó**, **(3) cờ biên dịch/chạy**, và **(4) cú pháp đúng của phiên bản**.

## 2. Bảng mốc phiên bản cần biết

| Tính năng | Trạng thái theo mốc liên quan | Ghi nhớ khi học Java 17+ |
|---|---|---|
| **Switch expression** với `case ->` và `yield` | Ổn định từ **Java 14** | Có thể dùng ngay trên JDK 17, không cần preview |
| **Pattern matching cho `instanceof`** | Ổn định từ **Java 16** | `o instanceof String s` hợp lệ trên JDK 17 |
| **Record class** | Ổn định từ **Java 16** | `record Point(int x, int y) {}` khác với record *pattern* |
| **Sealed classes** | Ổn định từ **Java 17** | Có thể dùng `sealed`/`permits` trên JDK 17 |
| **Pattern matching cho `switch`** | Preview qua các bản **Java 17–20**; ổn định từ **Java 21** | Không dùng switch type patterns như tính năng final của Java 17 |
| **Guard `when` trong pattern switch** | Xuất hiện ở phiên bản preview sau Java 17 (Java 19/20); ổn định cùng pattern switch ở **Java 21** | Java 17 preview dùng `&&` trong nhãn guarded pattern, không dùng `when` |
| **Record patterns** | Preview trước Java 21; ổn định từ **Java 21** | Đừng nhầm `record` đã ổn định trong Java 17 với destructuring pattern của record |

**Cảnh báo quan trọng:** Một tính năng có thể là **preview ở phiên bản A** nhưng đã **final ở phiên bản B**. Không thể nói chung chung “pattern switch là preview” mà không gắn số phiên bản.

Các ví dụ trong bài dùng **Java 17** (ổn định và preview riêng biệt) và **Java 21** (cú pháp final) để so sánh. Nếu bạn đang dùng JDK khác, phải tra cứu trạng thái cú pháp ở JDK đó trước khi đổi lệnh.

## 3. Tính năng ổn định có thể biên dịch mà không bật preview

Mẫu `instanceof` ở Java 17:

~~~java
static String explain(Object value) {
    if (value instanceof String text) {
        return "TEXT:" + text;
    }
    return "NOT_STRING";
}
~~~

Với `value = "Java"`, biểu thức `instanceof String text` kiểm tra kiểu và gán biến mẫu `text` trong luồng đã xác nhận khớp. Với null hoặc object không phải String, biểu thức trả false; Java không ném NPE chỉ vì kiểm tra `instanceof` trên null.

Tương tự, **switch expression** đã final trước Java 17:

~~~java
static String status(int code) {
    return switch (code) {
        case 1 -> "NEW";
        case 2 -> "RUNNING";
        default -> "UNKNOWN";
    };
}
~~~

Không có mẫu kiểu ở ví dụ này: các nhãn `case 1`/`case 2` là **hằng số thông thường**. Việc thấy từ khóa `switch`, `->` hoặc `yield` **không đồng nghĩa** đoạn code đang sử dụng tính năng preview.

## 4. Biên dịch preview đúng phiên bản

Với chương trình chứa cú pháp preview của **Java 17**, trên **JDK 17**, dùng:

~~~bash
javac --enable-preview --release 17 -encoding UTF-8 PreviewSwitch17.java
java --enable-preview -cp . PreviewSwitch17
~~~

**Giải thích:**

- `--enable-preview`: cho phép tính năng preview ở phiên bản đang biên dịch/chạy.
- `--release 17`: biên dịch theo API/ngôn ngữ mục tiêu Java 17.
- `java --enable-preview`: cần để chạy class sử dụng tính năng preview.
- Đối với preview, bản `javac` thông thường **chỉ cho bật preview của chính phiên bản JDK biên dịch**. Không dùng JDK 21 để cố bật preview của Java 17 bằng `--release 17`.
- Class được biên dịch với preview có đánh dấu class-file tương ứng; **không coi** nó là một class Java 17 ổn định có thể tùy ý chạy trên các JDK khác.

Với **code Java 17 ổn định**:

~~~bash
javac --release 17 -encoding UTF-8 StablePatterns17.java
java -cp . StablePatterns17
~~~

Không cần bật preview. Với **code Java 21 sử dụng pattern switch đã final**:

~~~bash
javac --release 21 -encoding UTF-8 FinalSwitch21.java
java -cp . FinalSwitch21
~~~

Không cần `--enable-preview` khi dùng các tính năng pattern switch chuẩn Java 21.

**Tránh ngộ nhận:** Cờ preview **không biến** cú pháp Java 21 `when` thành cú pháp hợp lệ trên Java 17. Cờ chỉ mở các tính năng preview **thực sự có trong phiên bản đó**.

## 5. Java 17 preview: guarded pattern dùng `&&`

Cú pháp guarded pattern trong JEP 406 (preview Java 17) có thể viết:

~~~java
// Chỉ hợp lệ khi dùng JDK 17 với --enable-preview:
return switch (value) {
    case String text && text.length() >= 5 -> "LONG";
    case String text -> "SHORT";
    default -> "OTHER";
};
~~~

- Selector cần có kiểu phù hợp, ở đây là `Object`.
- Phần `String text` là **mẫu kiểu**.
- Guard `&& text.length() >= 5` là cú pháp của **preview Java 17**, được xét sau khi mẫu `String` khớp.
- Nếu guard false, switch thử nhãn tiếp theo.
- Mẫu String không guard được đặt **sau** nhãn guarded để không che nhãn cụ thể.

Không viết `case String text when ...` trong file Java 17 rồi chỉ thêm `--enable-preview`: **`when` chưa phải cú pháp preview của Java 17**.

## 6. Java 21 final: guarded pattern dùng `when`

Trên JDK 21, biểu đạt logic tương ứng bằng:

~~~java
return switch (value) {
    case null -> "NULL";
    case String text when text.length() >= 5 -> "LONG";
    case String text -> "SHORT";
    default -> "OTHER";
};
~~~

Với cùng một giá trị String, các nhánh kiểu và guard quyết định kết quả như ví dụ Java 17 preview. Nhưng Java 21 dùng `when` để phân tách mẫu kiểu khỏi điều kiện bảo vệ.

| Ý định | Java 17 preview | Java 21 final |
|---|---|---|
| Chọn một String có độ dài ít nhất 5 | `case String s && s.length() >= 5 ->` | `case String s when s.length() >= 5 ->` |
| Chọn một String còn lại | `case String s ->` | `case String s ->` |
| Xử lý null | Nên guard null ở ngoài theo hợp đồng Java 17 | Có thể dùng `case null ->` |

**Chú ý:** Đoạn mã Java 21 có thể còn yêu cầu các nhánh đủ để switch expression tạo kết quả. Với selector Object, `default` là một cách xử lý trường hợp không khớp các mẫu liệt kê.

## 7. Điều gì không thay đổi giữa các phiên bản?

Dù Java 17 preview viết guarded label bằng `&&` còn Java 21 final dùng `when`, guard chỉ được đánh giá **sau khi** type pattern khớp. Nếu guard false, switch thử nhãn tiếp theo; nếu nhiều nhãn cùng khớp, nhãn đứng trước được chọn. Biến mẫu chỉ hợp lệ ở nhánh và guard đã xác nhận kiểu.

Thứ tự còn liên quan **dominance**: đặt `case CharSequence cs` trước `case String s` làm nhãn String không thể tới; tương tự, nhãn `String` không guard có thể che nhãn String có guard phía sau. Đổi phiên bản cú pháp không giải quyết thứ tự sai. Trong Java 17 ổn định, chuỗi `if (value instanceof String s && ...)` diễn đạt guard mà không có phép kiểm tra dominance giữa các nhánh `if`.

## 8. Khi nào cần dùng preview?

**Ưu tiên code ổn định** nếu:

- Dự án phải biên dịch trên Java 17 bằng lệnh `javac --release 17` thông thường.
- Đang viết thư viện hoặc bài học cho nhiều môi trường JDK khác nhau.
- Muốn tránh phụ thuộc vào cú pháp có thể thay đổi.
- Hệ thống build, CI, IDE và runtime chưa bảo đảm cùng phiên bản và cờ preview.

**Chỉ nên cân nhắc preview** khi thực sự nghiên cứu tính năng mới ở đúng phiên bản, chấp nhận điều kiện triển khai và đã kiểm soát toàn bộ toolchain. Với bài học về pattern switch có guard:

- **JDK 17:** dùng `instanceof` pattern ổn định + `if`/`&&` là lựa chọn an toàn.
- **JDK 17 preview:** có thể thử guard dùng `&&`, nhưng phải bật preview cả lúc compile và run.
- **JDK 21 trở lên:** có thể dùng `when` final theo ngữ nghĩa JDK 21.

Không đổi ngôn ngữ đích hoặc nâng JDK chỉ vì một ví dụ trên mạng có vẻ ngắn hơn; cân nhắc chính sách tương thích của dự án.

## 9. Cách kiểm tra môi trường và lỗi thường gặp

Trước hết mở terminal:

~~~bash
java -version
javac -version
~~~

Hai lệnh cho biết **runtime** và **compiler** đang được gọi từ PATH. Trong một máy có nhiều JDK, chúng có thể vô tình trỏ tới phiên bản khác nhau; cần chọn đúng JDK qua `JAVA_HOME`, PATH hoặc cấu hình IDE/build tool.

| Biểu hiện | Nguyên nhân thường gặp | Hướng xử lý |
|---|---|---|
| `case String s when ...` lỗi cú pháp trên Java 17 | `when` không phải cú pháp ở bản này | Dùng `instanceof` Java 17 hoặc JDK 21 |
| Pattern switch Java 17 báo preview disabled | Chưa bật `--enable-preview` | Bật preview compile/run đúng trên JDK 17 |
| Java báo cần `--enable-preview` khi chạy class preview | Runtime chưa cho phép preview | Chạy `java --enable-preview ...` đúng JDK |
| Dùng JDK 21 kèm `--enable-preview --release 17` | Preview chỉ dành cho release của chính compiler | Dùng JDK 17 để thử preview 17; hoặc chuyển sang syntax final 21 |
| Compile Java 21 source bằng `--release 17` | Target Java 17 không biết tính năng Java 21 | Viết code tương thích 17 hoặc đổi target một cách chủ đích |
| Coi `record` và record pattern là cùng một tính năng | `record` ổn định trước record pattern | Tách mốc tính năng theo JEP/phiên bản |
| `instanceof String s` bị nhầm là preview Java 17 | Pattern instanceof đã final từ 16 | Bỏ cờ preview không cần thiết |
| `switch (int)` với `case ->` bị nhầm là preview | Switch expression/rules đã ổn định trước Java 17 | Phân biệt case hằng với type pattern |
| Dùng `case null` tùy tiện trên switch Java 17 ổn định | Khác khả năng và ngữ nghĩa của JDK 21 | Guard null bằng `if` trước |
| Mẫu String bao quát đứng trước mẫu guarded | Nhãn sau bị dominated | Nhãn cụ thể/guard trước nhãn chung |
| IDE báo lỗi nhưng CLI đúng, hoặc ngược lại | Chênh lệch language level/JDK/cờ preview | Đồng bộ project SDK, compiler, runtime và flags |

**Ghi chú về tương thích:** `--release 17` dùng để yêu cầu biên dịch nhắm tới Java 17 và API chuẩn tương ứng, **không** phải lời hứa rằng mọi cú pháp preview mới hơn đều được “dịch ngược” về Java 17.

## 10. Chương trình 1 — Java 17 ổn định, không preview

Tạo file **`StablePatterns17.java`**:

~~~java
import java.util.Locale;

public final class StablePatterns17 {
    private StablePatterns17() {}

    static String describe(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return "TEXT:" + text.strip().toUpperCase(Locale.ROOT);
        }
        if (value instanceof String) {
            return "BLANK_TEXT";
        }
        if (value instanceof Integer number) {
            return "INTEGER:" + number;
        }
        if (value == null) {
            return "NULL";
        }
        return "OTHER";
    }

    static String status(int code) {
        return switch (code) {
            case 1 -> "NEW";
            case 2 -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    public static void main(String[] args) {
        System.out.println("stable17 text=" + describe(" Java "));
        System.out.println("stable17 blank=" + describe("   "));
        System.out.println("stable17 int=" + describe(Integer.valueOf(7)));
        System.out.println("stable17 null=" + describe(null));
        System.out.println("stable17 other=" + describe(Boolean.TRUE));
        System.out.println("stable17 switch=" + status(2));
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 StablePatterns17.java
java -cp . StablePatterns17
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\StablePatterns17.java
java -cp . StablePatterns17
~~~

**Exact expected stdout:**

~~~text
stable17 text=TEXT:JAVA
stable17 blank=BLANK_TEXT
stable17 int=INTEGER:7
stable17 null=NULL
stable17 other=OTHER
stable17 switch=RUNNING
~~~

Ở đây `instanceof` pattern và switch expression đều **ổn định trong Java 17**, nên không bật preview.

## 11. Chương trình 2 — switch pattern preview trên đúng JDK 17

Tạo file **`PreviewSwitch17.java`**, dùng preview JEP 406, không chứa `when`:

~~~java
public final class PreviewSwitch17 {
    private PreviewSwitch17() {}

    static String classify(Object value) {
        return switch (value) {
            case String text && text.isBlank() -> "BLANK";
            case String text && text.length() >= 5 -> "LONG";
            case String text -> "SHORT";
            case Integer number -> "INTEGER:" + number;
            default -> "OTHER";
        };
    }

    public static void main(String[] args) {
        System.out.println("preview17 blank=" + classify("  "));
        System.out.println("preview17 long=" + classify("World"));
        System.out.println("preview17 short=" + classify("Go"));
        System.out.println("preview17 int=" + classify(7));
        System.out.println("preview17 other=" + classify(Boolean.TRUE));
    }
}
~~~

**Bash (Linux/macOS), bắt buộc JDK 17:**

~~~bash
javac --enable-preview --release 17 -encoding UTF-8 PreviewSwitch17.java
java --enable-preview -cp . PreviewSwitch17
~~~

**PowerShell (Windows), bắt buộc JDK 17:**

~~~powershell
javac --enable-preview --release 17 -encoding UTF-8 .\PreviewSwitch17.java
java --enable-preview -cp . PreviewSwitch17
~~~

**Exact expected stdout của chương trình:**

~~~text
preview17 blank=BLANK
preview17 long=LONG
preview17 short=SHORT
preview17 int=INTEGER:7
preview17 other=OTHER
~~~

**Lưu ý về thông báo của compiler:** JDK 17 có thể in thêm **thông báo preview** trên stderr khi biên dịch; phần expected stdout trên chỉ là **đầu ra chương trình**. Không cần chỉnh lệnh để che thông báo đó.

Vì `classify(null)` không được gọi, không có nhánh xử lý null trong chương trình demo. Nếu cần nhận null, hãy kiểm tra **trước switch** theo hợp đồng API, thay vì giả định `default` bắt mọi giá trị.

## 12. Chương trình 3 — cú pháp final `when` trên JDK 21+

Tạo file **`FinalSwitch21.java`**:

~~~java
public final class FinalSwitch21 {
    private FinalSwitch21() {}

    static String classify(Object value) {
        return switch (value) {
            case null -> "NULL";
            case String text when text.isBlank() -> "BLANK";
            case String text when text.length() >= 5 -> "LONG";
            case String text -> "SHORT";
            case Integer number -> "INTEGER:" + number;
            default -> "OTHER";
        };
    }

    public static void main(String[] args) {
        System.out.println("final21 null=" + classify(null));
        System.out.println("final21 blank=" + classify("  "));
        System.out.println("final21 long=" + classify("World"));
        System.out.println("final21 short=" + classify("Go"));
        System.out.println("final21 int=" + classify(7));
        System.out.println("final21 other=" + classify(Boolean.TRUE));
    }
}
~~~

**Bash (Linux/macOS), JDK 21+:**

~~~bash
javac --release 21 -encoding UTF-8 FinalSwitch21.java
java -cp . FinalSwitch21
~~~

**PowerShell (Windows), JDK 21+:**

~~~powershell
javac --release 21 -encoding UTF-8 .\FinalSwitch21.java
java -cp . FinalSwitch21
~~~

**Exact expected stdout:**

~~~text
final21 null=NULL
final21 blank=BLANK
final21 long=LONG
final21 short=SHORT
final21 int=INTEGER:7
final21 other=OTHER
~~~

Đây là tính năng **chính thức từ Java 21**, vì thế **không dùng** `--enable-preview` cho riêng pattern matching for switch của chương trình này.

## 13. Cách đọc sự khác nhau giữa ba ví dụ

| Tiêu chí | `StablePatterns17.java` | `PreviewSwitch17.java` | `FinalSwitch21.java` |
|---|---|---|---|
| JDK để biên dịch | 17+ với target 17 | **Đúng JDK 17** | 21+ với target 21 |
| `--enable-preview` | Không | **Có: compile và run** | Không |
| Pattern matching cho `instanceof` | Có | Không cần dùng riêng | Không cần dùng riêng |
| Pattern matching cho `switch` | Không | **Preview** | **Final** |
| Cú pháp guard | `instanceof T t && ...` trong `if` | `case T t && ...` | `case T t when ...` |
| Xử lý `null` | `if (value == null)` | Guard trước switch nếu cần | `case null` có thể dùng |
| Mục tiêu | Code Java 17 tương thích | Thử nghiệm cú pháp preview năm 2021 | Code switch pattern hiện đại ổn định |

Cùng một nhu cầu phân loại theo kiểu có thể có nhiều cách viết; **không có cách viết nào được phép “copy” giữa các bản JDK mà bỏ qua phiên bản**.

## 14. Các bẫy cần nhớ

1. **Final không đồng nghĩa tính năng đã tồn tại ở mọi JDK cũ:** cú pháp final của Java 21 vẫn không hợp lệ nếu target Java 17.
2. **Preview không phải cú pháp bất biến:** guard `&&` của switch preview Java 17 khác `when` của Java 21.
3. **Một file dùng preview có thể tạo class không chạy theo cách bình thường:** cần cờ runtime và JDK phù hợp.
4. **`case ->` không tự động là preview:** switch rules và switch expressions đã ổn định trước Java 17.
5. **Một Java 17 `record` hợp lệ không suy ra record patterns cũng final trong Java 17.**
6. **Đừng nhầm warning với lỗi chương trình:** compiler có thể cảnh báo preview, trong khi chương trình vẫn chạy với cờ đúng.
7. **Đừng tin `default` luôn bắt null** trong switch pattern: xử lý null riêng theo phiên bản và cú pháp.
8. **Thứ tự nhãn là ngữ nghĩa:** guard false thử case khác, nhưng pattern rộng không guard trước pattern hẹp có thể bị compiler báo dominated.
9. **Không ép dùng preview cho code production** chỉ để viết ít dòng hơn khi phiên bản ổn định đã có giải pháp rõ ràng.
10. **Đồng bộ IDE, build và CLI:** các tùy chọn language level khác nhau có thể khiến cùng file compile ở nơi này nhưng lỗi nơi khác.

## 15. Bài tập có đáp án

### Bài A — cú pháp nào chạy ổn định trên Java 17?

~~~java
Object obj = "Java";
if (obj instanceof String s) {
    System.out.println(s.length());
}
~~~

**Đáp án:** Chạy ổn định trên JDK 17, in `4`. `instanceof` pattern đã final từ Java 16; không cần preview.

### Bài B — sửa nhầm phiên bản

~~~java
// Người học dùng JDK 17:
// case String s when s.isBlank() -> "BLANK"
~~~

**Đáp án:** Java 17 **không hỗ trợ `when`**. Có thể dùng `if (obj instanceof String s && s.isBlank())` mà không cần preview. Nếu cố tình nghiên cứu preview Java 17, dùng `case String s && s.isBlank() -> ...` và bật `--enable-preview` đúng trên JDK 17. Nếu muốn `when` final, cần JDK 21+ và target 21 trở lên.

### Bài C — lệnh nào cho phép preview Java 17?

~~~text
A. javac --release 17 PreviewSwitch17.java
B. javac --enable-preview --release 17 PreviewSwitch17.java
C. javac --enable-preview --release 21 PreviewSwitch17.java
~~~

**Đáp án:** **B**, khi sử dụng **JDK 17**. Sau biên dịch, chạy `java --enable-preview -cp . PreviewSwitch17` trên JDK 17. Lựa chọn C không phải cách yêu cầu “preview Java 17”.

### Bài D — dominance và thứ tự nhãn

~~~java
// JDK 21, đoạn SAI về thứ tự:
switch (obj) {
    case CharSequence sequence -> System.out.println("SEQUENCE");
    case String text -> System.out.println("STRING");
    default -> System.out.println("OTHER");
}
~~~

**Đáp án:** `String` là một `CharSequence`, nên nhãn String phía sau bị che hoàn toàn; sửa bằng cách **đặt `case String text` trước `case CharSequence sequence`**.

### Bài E — tính năng đã final có cần preview không?

Một file JDK 21 chỉ dùng `case String s when s.length() > 3 -> ...` trong pattern switch đã final. Có cần `--enable-preview`?

**Đáp án:** **Không**. Dùng `javac --release 21 ...` và `java ...` trên JDK 21. Preview chỉ cần khi đang dùng một **tính năng khác** chưa ổn định của JDK cụ thể.

## 16. Checklist và kết luận

- [ ] Luôn gắn nhận định final/preview với **số phiên bản Java**.
- [ ] Biết `instanceof` pattern đã final từ Java 16, ổn định trên Java 17.
- [ ] Biết switch expression `->`/`yield` final từ Java 14.
- [ ] Biết pattern matching cho switch preview trên Java 17–20, final từ Java 21.
- [ ] Biết Java 17 switch preview dùng guard `&&`; Java 21 final dùng `when`.
- [ ] Biết `--enable-preview` cần bật ở **compile và run** nếu class dùng preview Java 17.
- [ ] Không cố yêu cầu JDK mới bật preview của release cũ.
- [ ] Biết kiểm tra `java -version`, `javac -version` và cờ `--release`.
- [ ] Phân biệt record class đã final với record patterns được final muộn hơn.
- [ ] Hiểu guard chỉ chạy khi type pattern khớp và thứ tự case có thể đổi kết quả.
- [ ] Hiểu dominance và null handling trong switch pattern theo phiên bản.
- [ ] Chạy `StablePatterns17.java`, `PreviewSwitch17.java` và `FinalSwitch21.java` trên các JDK tương ứng, đối chiếu expected stdout.

**Kết luận:** Một cú pháp trong Java có thể **preview ở bản trước và ổn định ở bản sau**, đồng thời **đổi cú pháp** khi hoàn thiện. Đối với mẫu kiểm tra kiểu, Java 17 dùng `instanceof` pattern ổn định; pattern switch của Java 17 là preview với guard `&&`, còn Java 21 đã final pattern switch cùng guard `when`. Muốn học hoặc triển khai đúng, hãy luôn kiểm tra **phiên bản, cú pháp, trạng thái và cờ compile/run** trước khi sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
