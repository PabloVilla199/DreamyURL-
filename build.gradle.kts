plugins {
    id("jacoco")
}

repositories {
    mavenCentral()
}

tasks.register<JacocoReport>("jacocoRootReport") {
    group = "Verification"
    description = "Genera un reporte unificado de cobertura para Arquitectura Hexagonal"

    val reportableProjects = subprojects.filter { it.pluginManager.hasPlugin("java") || it.pluginManager.hasPlugin("kotlin") }

    dependsOn(reportableProjects.map { it.tasks.named("test") })
    dependsOn(reportableProjects.map { it.tasks.named("jacocoTestReport") })

    executionData.setFrom(project.fileTree(".") {
        include("**/build/jacoco/test.exec")
    })

    
    classDirectories.setFrom(files(reportableProjects.map { proj ->
        proj.extensions.getByType(SourceSetContainer::class.java)
            .named("main").get().output
    }))
    
    sourceDirectories.setFrom(files(reportableProjects.map { proj ->
        proj.extensions.getByType(SourceSetContainer::class.java)
            .named("main").get().allSource.srcDirs
    }))

    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}