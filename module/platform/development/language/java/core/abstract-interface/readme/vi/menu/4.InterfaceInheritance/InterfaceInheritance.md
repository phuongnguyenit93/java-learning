# Interface Inheritance

## <a id="interface-extends-interface">Interface extend interface</a>
Interface có thể extend một hoặc nhiều interface. Subinterface inherit instance-method contract, có thể thêm method hoặc refine compatible return type. Implementer của subinterface phải thỏa combined contract.

## <a id="multiple-interface-hierarchy">Multiple parent interface</a>
Multiple interface inheritance compose type chứ không compose object state. Nó an toàn khi inherited contract compatible. Identical abstract signature merge tự nhiên; return type incompatible hoặc default conflict có thể làm hierarchy invalid hoặc cần resolve explicit.

## <a id="interface-redeclaration">Redeclare/refine inherited method</a>
Subinterface có thể redeclare inherited method để thêm documentation/annotation, narrow covariant return hoặc biến default method thành abstract requirement lại. Redeclaration nên làm contract rõ hơn, không chỉ duplicate vô ích.
