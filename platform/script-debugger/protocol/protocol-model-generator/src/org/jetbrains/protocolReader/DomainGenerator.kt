package org.jetbrains.protocolReader

class DomainGenerator(val generator: Generator, val domain: ProtocolMetaModel.Domain) {

  fun registerTypes() {
    if (domain.types() != null) {
      for (type in domain.types()!!) {
        generator.typeMap.getTypeData(domain.domain(), type.id()).setType(type)
      }
    }
  }

  throws(javaClass<IOException>())
  fun generateCommandsAndEvents() {
    val requestsFileUpdater = generator.startJavaFile(generator.naming.params.getPackageNameVirtual(domain.domain()), "Requests.java")
    val out = requestsFileUpdater.out
    out.append("import org.jetbrains.annotations.NotNull;").newLine()
    out.append("import org.jetbrains.jsonProtocol.Request;").newLine()
    out.newLine().append("public final class ").append("Requests")
    out.openBlock()

    var isFirst = true

    for (command in domain.commands()) {
      val hasResponse = command.returns() != null

      var onlyMandatoryParams = true
      val params = command.parameters()
      val hasParams = params != null && !params.isEmpty()
      if (hasParams) {
        for (parameter in params!!) {
          if (parameter.optional()) {
            onlyMandatoryParams = false
          }
        }
      }

      val returnType = if (hasResponse) generator.naming.commandResult.getShortName(command.name()) else "Void"
      if (onlyMandatoryParams) {
        if (isFirst) {
          isFirst = false
        }
        else {
          out.newLine().newLine()
        }
        out.append("@NotNull").newLine().append("public static Request<")
        out.append(returnType)
        out.append(">").space().append(fixMethodName(command.name())).append("(")

        val classScope = OutputClassScope(this, generator.naming.params.getFullName(domain.domain(), command.name()))
        val parameterTypes = if (hasParams) arrayOfNulls<BoxableType>(params!!.size()) else null
        if (hasParams) {
          classScope.writeMethodParameters<ProtocolMetaModel.Parameter>(out, params!!, parameterTypes!!)
        }

        out.append(')').openBlock()

        if (hasParams) {
          out.append("V8SimpleRequest<").append(returnType).append(">").append(" r =")
        }
        else {
          out.append("return")
        }

        out.append(" new V8SimpleRequest<").append(returnType).append(">(\"")
        if (!domain.domain().isEmpty()) {
          out.append(domain.domain()).append('.')
        }
        out.append(command.name()).append("\")").semi()

        if (hasParams) {
          classScope.writeWriteCalls<ProtocolMetaModel.Parameter>(out, params!!, parameterTypes!!, "r")
          out.newLine().append("return r").semi()
        }

        out.closeBlock()
      }
      else {
        generateRequest(command, returnType)
      }

      if (hasResponse) {
        val fileUpdater = generator.startJavaFile(generator.naming.commandResult, domain, command.name())
        generateJsonProtocolInterface(fileUpdater.out, generator.naming.commandResult.getShortName(command.name()), command.description(), command.returns(), null)
        fileUpdater.update()
        generator.jsonProtocolParserClassNames.add(generator.naming.commandResult.getFullName(domain.domain(), command.name()).getFullText())
        generator.parserRootInterfaceItems.add(ParserRootInterfaceItem(domain.domain(), command.name(), generator.naming.commandResult))
      }
    }

    out.closeBlock()
    requestsFileUpdater.update()

    if (domain.events() != null) {
      for (event in domain.events()!!) {
        generateEvenData(event)
        generator.jsonProtocolParserClassNames.add(generator.naming.eventData.getFullName(domain.domain(), event.name()).getFullText())
        generator.parserRootInterfaceItems.add(ParserRootInterfaceItem(domain.domain(), event.name(), generator.naming.eventData))
      }
    }
  }

  throws(javaClass<IOException>())
  private fun generateRequest(command: ProtocolMetaModel.Command, returnType: String) {
    val baseTypeBuilder = object : TextOutConsumer {
      override fun append(out: TextOutput) {
        out.space().append("extends ").append(generator.naming.requestClassName).append('<').append(returnType).append('>')
      }
    }

    val memberBuilder = object : TextOutConsumer {
      override fun append(out: TextOutput) {
        out.append("@NotNull").newLine().append("@Override").newLine().append("public String getMethodName()").openBlock()
        out.append("return \"")
        if (!domain.domain().isEmpty()) {
          out.append(domain.domain()).append('.')
        }
        out.append(command.name()).append("\";").closeBlock()
      }
    }
    generateTopLevelOutputClass(generator.naming.params, command.name(), command.description(), baseTypeBuilder, memberBuilder, command.parameters())
  }

  fun generateCommandAdditionalParam(type: ProtocolMetaModel.StandaloneType) {
    generateTopLevelOutputClass(generator.naming.additionalParam, type.id(), type.description(), null, null, type.properties())
  }

  private fun <P : ItemDescriptor.Named> generateTopLevelOutputClass(nameScheme: ClassNameScheme, baseName: String, description: String?, baseType: TextOutConsumer?, additionalMemberText: TextOutConsumer?, properties: List<P>?) {
    val fileUpdater = generator.startJavaFile(nameScheme, domain, baseName)
    if (nameScheme == generator.naming.params) {
      fileUpdater.out.append("import org.jetbrains.annotations.NotNull;").newLine().newLine()
    }
    generateOutputClass(fileUpdater.out, nameScheme.getFullName(domain.domain(), baseName), description, baseType, additionalMemberText, properties)
    fileUpdater.update()
  }

  private fun <P : ItemDescriptor.Named> generateOutputClass(out: TextOutput, classNamePath: NamePath, description: String?, baseType: TextOutConsumer?, additionalMemberText: TextOutConsumer?, properties: List<P>?) {
    out.doc(description)
    out.append("public final class ").append(classNamePath.lastComponent)
    if (baseType == null) {
      out.append(" extends ").append("org.jetbrains.jsonProtocol.OutMessage")
    }
    else {
      baseType.append(out)
    }

    val classScope = OutputClassScope(this, classNamePath)
    if (additionalMemberText != null) {
      classScope.addMember(additionalMemberText)
    }

    out.openBlock()
    classScope.generate<P>(out, properties)
    classScope.writeAdditionalMembers(out)
    out.closeBlock()
  }

  fun createStandaloneOutputTypeBinding(type: ProtocolMetaModel.StandaloneType, name: String): StandaloneTypeBinding {
    return Generator.switchByType<StandaloneTypeBinding>(type, MyCreateStandaloneTypeBindingVisitorBase(this, type, name))
  }

  fun createStandaloneInputTypeBinding(type: ProtocolMetaModel.StandaloneType): StandaloneTypeBinding {
    return Generator.switchByType<StandaloneTypeBinding>(type, object : CreateStandaloneTypeBindingVisitorBase(this, type) {
      override fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): StandaloneTypeBinding {
        return createStandaloneObjectInputTypeBinding(type, properties)
      }

      override fun visitEnum(enumConstants: List<String>): StandaloneTypeBinding {
        return createStandaloneEnumInputTypeBinding(type, enumConstants, TypeData.Direction.INPUT)
      }

      override fun visitArray(items: ProtocolMetaModel.ArrayItemType): StandaloneTypeBinding {
        val resolveAndGenerateScope = object : ResolveAndGenerateScope {
          // This class is responsible for generating ad hoc type.
          // If we ever are to do it, we should generate into string buffer and put strings
          // inside TypeDef class.
          override fun getDomainName(): String {
            return domain.domain()
          }

          override fun getTypeDirection(): TypeData.Direction {
            return TypeData.Direction.INPUT
          }

          override fun <T : ItemDescriptor> resolveType(typedObject: T): TypeDescriptor {
            throw UnsupportedOperationException()
          }

          override fun generateNestedObject(description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?) = throw UnsupportedOperationException()
        }
        val itemBoxableType = generator.resolveType(items, resolveAndGenerateScope).type

        val arrayType = ListType(itemBoxableType)
        val target = object : StandaloneTypeBinding.Target {
          override fun resolve(context: StandaloneTypeBinding.Target.ResolveContext): BoxableType {
            return arrayType
          }
        }

        return createTypedefTypeBinding(type, target, generator.naming.inputTypedef, TypeData.Direction.INPUT)
      }
    })
  }

  fun createStandaloneObjectInputTypeBinding(type: ProtocolMetaModel.StandaloneType, properties: List<ProtocolMetaModel.ObjectProperty>?): StandaloneTypeBinding {
    val name = type.id()
    val fullTypeName = generator.naming.inputValue.getFullName(domain.domain(), name)
    generator.jsonProtocolParserClassNames.add(fullTypeName.getFullText())

    return object : StandaloneTypeBinding {
      override fun getJavaType(): BoxableType {
        return StandaloneType(fullTypeName, "writeMessage")
      }

      throws(javaClass<IOException>())
      override fun generate() {
        val className = generator.naming.inputValue.getFullName(domain.domain(), name)
        val fileUpdater = generator.startJavaFile(generator.naming.inputValue, domain, name)
        val out = fileUpdater.out
        if (type.description() != null) {
          out.doc(type.description())
        }

        out.append("@org.jetbrains.jsonProtocol.JsonType").newLine()
        out.append("public interface ").append(className.lastComponent).openBlock()
        val classScope = InputClassScope(this@DomainGenerator, className)
        if (properties != null) {
          classScope.generateDeclarationBody(out, properties)
        }
        classScope.writeAdditionalMembers(out)
        out.closeBlock()
        fileUpdater.update()
      }

      override fun getDirection(): TypeData.Direction? {
        return TypeData.Direction.INPUT
      }
    }
  }

  fun createStandaloneEnumInputTypeBinding(type: ProtocolMetaModel.StandaloneType, enumConstants: List<String>, direction: TypeData.Direction): StandaloneTypeBinding {
    val name = type.id()
    return object : StandaloneTypeBinding {
      override fun getJavaType(): BoxableType {
        return StandaloneType(generator.naming.inputEnum.getFullName(domain.domain(), name), "writeEnum")
      }

      throws(javaClass<IOException>())
      override fun generate() {
        val fileUpdater = generator.startJavaFile(generator.naming.inputEnum, domain, name)
        fileUpdater.out.doc(type.description())
        Enums.appendEnums(enumConstants, generator.naming.inputEnum.getShortName(name), true, fileUpdater.out)
        fileUpdater.update()
      }

      override fun getDirection(): TypeData.Direction? {
        return direction
      }
    }
  }

  /**
   * Typedef is an empty class that just holds description and
   * refers to an actual type (such as String).
   */
  fun createTypedefTypeBinding(type: ProtocolMetaModel.StandaloneType, target: StandaloneTypeBinding.Target, nameScheme: ClassNameScheme, direction: TypeData.Direction?): StandaloneTypeBinding {
    val name = type.id()
    val typedefJavaName = nameScheme.getFullName(domain.domain(), name)
    val actualJavaType = target.resolve(object : StandaloneTypeBinding.Target.ResolveContext {
      override fun generateNestedObject(shortName: String, description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType {
        val classNamePath = NamePath(shortName, typedefJavaName)
        if (direction == null) {
          throw RuntimeException("Unsupported")
        }

        when (direction) {
          TypeData.Direction.INPUT -> throw RuntimeException("TODO")
          TypeData.Direction.OUTPUT -> {
            val out = TextOutput(StringBuilder())
            generateOutputClass(out, classNamePath, description, null, null, properties)
          }
          else -> throw RuntimeException()
        }
        return StandaloneType(NamePath(shortName, typedefJavaName), "writeMessage")
      }
    })

    return object : StandaloneTypeBinding {
      override fun getJavaType(): BoxableType {
        return actualJavaType
      }

      override fun generate() {
      }

      override fun getDirection(): TypeData.Direction? {
        return direction
      }
    }
  }

  throws(javaClass<IOException>())
  private fun generateEvenData(event: ProtocolMetaModel.Event) {
    val className = generator.naming.eventData.getShortName(event.name())
    val fileUpdater = generator.startJavaFile(generator.naming.eventData, domain, event.name())
    val domainName = domain.domain()
    val fullName = generator.naming.eventData.getFullName(domainName, event.name()).getFullText()
    generateJsonProtocolInterface(fileUpdater.out, className, event.description(), event.parameters(), object : TextOutConsumer {
      override fun append(out: TextOutput) {
        out.newLine().append("org.jetbrains.wip.protocol.WipEventType<").append(fullName).append("> TYPE").newLine()
        out.append("\t= new org.jetbrains.wip.protocol.WipEventType<").append(fullName).append(">")
        out.append("(\"").append(domainName).append('.').append(event.name()).append("\", ").append(fullName).append(".class)").openBlock()
        run {
          out.append("@Override").newLine().append("public ").append(fullName).append(" read(")
          out.append(generator.naming.inputPackage).append('.').append(Generator.READER_INTERFACE_NAME + " protocolReader, ").append(JSON_READER_PARAMETER_DEF).append(")").openBlock()
          out.append("return protocolReader.").append(generator.naming.eventData.getParseMethodName(domainName, event.name())).append("(reader);").closeBlock()
        }
        out.closeBlock()
        out.semi()
      }
    })
    fileUpdater.update()
  }

  private fun generateJsonProtocolInterface(out: TextOutput, className: String, description: String?, parameters: List<ProtocolMetaModel.Parameter>?, additionalMembersText: TextOutConsumer?) {
    if (description != null) {
      out.doc(description)
    }
    out.append("@org.jetbrains.jsonProtocol.JsonType").newLine().append("public interface ").append(className).openBlock()
    val classScope = InputClassScope(this, NamePath(className, NamePath(ClassNameScheme.getPackageName(generator.naming.inputPackage, domain.domain()))))
    if (additionalMembersText != null) {
      classScope.addMember(additionalMembersText)
    }
    if (parameters != null) {
      classScope.generateDeclarationBody(out, parameters)
    }
    classScope.writeAdditionalMembers(out)
    out.closeBlock()
  }

  default object {

    fun fixMethodName(name: String): String {
      val i = name.indexOf("breakpoint")
      return if (i > 0) name.substring(0, i) + 'B' + name.substring(i + 1) else name
    }
  }
}