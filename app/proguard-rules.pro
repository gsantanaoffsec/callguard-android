# O CallScreeningService e instanciado pelo framework a partir do nome declarado
# no AndroidManifest.xml. Sem esta regra o R8 poderia renomear/remover a classe.
-keep class br.dev.callguard.screening.InsistentCallScreeningService { *; }
