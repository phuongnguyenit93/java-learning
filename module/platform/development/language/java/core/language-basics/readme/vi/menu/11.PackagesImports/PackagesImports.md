# Package và Import

## <a id="package-namespace">Package như namespace</a>
Package tham gia fully qualified name của type và nhóm các type liên quan. Package structure còn ảnh hưởng package-private/protected access. Directory convention thường mirror package name, nhưng language concept là package declaration.

## <a id="import-resolution">Import và name resolution</a>
Import cho phép source dùng simple type name; nó không copy code hay tự tạo runtime dependency. `java.lang` được import implicit, current package visible, và simple name conflict phải dùng qualification.

## <a id="static-import">Static import</a>
Static import cho phép dùng selected static member mà không prefix class. Nó hữu ích cho DSL-like code hoặc constant nhưng cũng có thể che ownership. Chỉ nên dùng khi nguồn của name vẫn rõ trong context.

## <a id="package-access">Package-private access boundary</a>
Không ghi access modifier tạo package-private access. Type/member chỉ accessible trong cùng package. Đây là boundary hữu ích cho implementation collaboration mà không mở surface ra public.
