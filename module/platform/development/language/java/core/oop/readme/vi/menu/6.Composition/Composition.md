# Composition

## <a id="composition-has-a">Composition và has-a</a>
Composition xây behavior bằng cách giữ reference tới collaborator thay vì inherit implementation. Service có thể delegate responsibility cho strategy, repository, formatter hay policy object và thay collaborator mà không đổi type hierarchy của chính nó.

## <a id="object-relationships">Dependency, association, aggregation và composition là modeling relationship</a>
Các term này mô tả độ mạnh relationship chứ không phải bốn Java runtime feature đặc biệt. Dependency thường là temporary use; association là relationship lâu hơn; aggregation diễn đạt weak/shared ownership; composition diễn đạt strong ownership/lifecycle. Java đều biểu diễn chúng bằng ordinary reference và convention.

## <a id="association-dependency">Dependency và long-lived association</a>
Method parameter/local collaborator có thể là dependency cho một operation, còn field thường biểu diễn association nằm trong object state. Distinction này giúp reasoning coupling/lifetime nhưng không nên ép nếu không mang giá trị design.

## <a id="ownership-lifecycle">Ownership/lifecycle trong aggregation và composition</a>
Composition thường nghĩa parent conceptually own part và lifecycle part gắn với parent; aggregation cho phép part tồn tại độc lập/shared. Java GC không enforce UML ownership nên contract phải thể hiện qua API design, mutation rule và object creation/retention.

## <a id="composition-vs-inheritance">Trade-off composition và inheritance</a>
Inheritance cho subtype polymorphism và shared implementation nhưng couple chặt với base class. Composition cho runtime configurability và contract nhỏ hơn nhưng cần delegation explicit. Ưu tiên composition để reuse behavior nếu không có subtype relationship thật sự.

## <a id="delegation">Delegation để reuse behavior</a>
Delegation là chuyển work cho object sở hữu behavior liên quan. Nó tách responsibility và cho substitution qua interface. Quá nhiều pass-through layer cũng tạo noise, nên delegate khi đó là boundary thật.
