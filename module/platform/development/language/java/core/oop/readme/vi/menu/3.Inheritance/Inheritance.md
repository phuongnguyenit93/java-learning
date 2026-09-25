# Inheritance

## <a id="is-a-subtyping">Inheritance, subtyping và is-a</a>
Class inheritance tạo subtype relationship: `Child` có thể dùng ở nơi superclass contract được yêu cầu. “Is-a” nên mang nghĩa behavioral substitutability, không chỉ “có chung field”. Nếu subtype không honor expectation của parent thì inheritance là reuse mechanism sai.

## <a id="inherited-state-behavior">Inherited state và behavior</a>
Subclass inherit accessible behavior và superclass portion nằm trong cùng object. Private superclass state vẫn tồn tại nhưng được access qua superclass behavior thay vì trực tiếp. Constructor không được inherit; subclass construction explicit/implicit gọi superclass constructor.

## <a id="inheritance-coupling">Inheritance coupling và fragile-base risk</a>
Inheritance couple subclass với implementation assumption, protected hook, initialization và overridable behavior của superclass. Change ở base class có thể ảnh hưởng subclass bất ngờ. Chỉ nên dùng khi abstraction thật sự là subtype contract, không chỉ để reuse code.
