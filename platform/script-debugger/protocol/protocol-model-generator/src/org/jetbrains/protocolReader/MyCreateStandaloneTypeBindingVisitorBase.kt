package org.jetbrains.protocolReader

class MyCreateStandaloneTypeBindingVisitorBase(private val generator: DomainGenerator, type: ProtocolMetaModel.StandaloneType, private val name: String) : CreateStandaloneTypeBindingVisitorBase(generator, type) {

  override fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): StandaloneTypeBinding {
    return object : StandaloneTypeBinding {
      override fun getJavaType(): BoxableType {
        return StandaloneType(generator.generator.naming.additionalParam.getFullName(generator.domain.domain(), name), "writeMessage")
      }

      override fun generate() {
        generator.generateCommandAdditionalParam(type)
      }

      override fun getDirection(): TypeData.Direction? {
        return TypeData.Direction.OUTPUT
      }
    }
  }

  override fun visitEnum(enumConstants: List<String>): StandaloneTypeBinding {
    throw RuntimeException()
  }

  override fun visitArray(items: ProtocolMetaModel.ArrayItemType): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, object : StandaloneTypeBinding.Target {
      override fun resolve(context: StandaloneTypeBinding.Target.ResolveContext): BoxableType {
        return ListType(generator.generator.resolveType(items, object : ResolveAndGenerateScope {
          // This class is responsible for generating ad hoc type.
          // If we ever are to do it, we should generate into string buffer and put strings inside TypeDef class
          override fun getDomainName() = generator.domain.domain()

          override fun getTypeDirection() = TypeData.Direction.OUTPUT

          override fun <T : ItemDescriptor> resolveType(typedObject: T) = throw UnsupportedOperationException()

          override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType {
            return context.generateNestedObject("Item", description, properties)
          }
        }).type)
      }
    }, generator.generator.naming.outputTypedef, TypeData.Direction.OUTPUT)
  }
}
