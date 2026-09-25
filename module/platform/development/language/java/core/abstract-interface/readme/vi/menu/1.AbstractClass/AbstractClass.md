# Abstract Class

## <a id="abstract-class-model">Abstract class như partial implementation + state</a>
Abstract class có thể có instance state, constructor, concrete method và abstract method. Nó phù hợp khi các subclass liên quan share cả common contract lẫn implementation/state có ý nghĩa trong cùng inheritance hierarchy.

## <a id="abstract-method">Contract của abstract method</a>
Abstract method khai báo signature nhưng không có implementation body. Concrete subclass phải implement inherited abstract contract trừ khi có inherited concrete implementation khác thỏa contract. Method vẫn tuân overriding, visibility và throws rule bình thường.

## <a id="abstract-constructor">Constructor của abstract class</a>
Abstract class không instantiate trực tiếp nhưng constructor của nó chạy khi construct mọi concrete subclass. Nó initialize superclass portion và có thể enforce superclass invariant. Tránh gọi overridable method từ đây vì cùng construction-order risk như superclass constructor khác.

## <a id="abstract-class-limits">Giới hạn instantiation và inheritance</a>
Abstract chỉ cấm direct instantiation, không cấm dùng làm reference type. Class chỉ extend một class. Dùng abstract class khi base implementation/state thật sự thuộc model; không dùng chỉ để gom helper không liên quan.
