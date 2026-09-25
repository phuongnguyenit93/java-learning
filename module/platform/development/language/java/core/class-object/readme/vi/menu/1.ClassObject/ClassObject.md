# Class và Object

## <a id="class-object-model">Mental model Class và Object</a>
Class định nghĩa type và implementation blueprint: field, method, constructor, nested type và initialization logic. Object là runtime instance có identity và instance state riêng. Nhiều object có thể dùng cùng class definition nhưng giữ field value khác nhau.

## <a id="fields-methods-state">State, behavior và instance identity</a>
Instance field biểu diễn state của từng object; instance method làm việc qua implicit reference `this`. Static member thuộc class-level context. Hai object có thể có state bằng nhau nhưng vẫn là hai identity khác nhau.

## <a id="object-reference-lifecycle">Reference reachability và boundary của object lifetime</a>
Variable giữ reference chứ không chứa object “bên trong”. Object lifetime phụ thuộc reachability/GC, không phụ thuộc lexical scope hay destructor explicit. Resource lifetime là chuyện khác: file/socket cần close deterministic thay vì chờ GC.
