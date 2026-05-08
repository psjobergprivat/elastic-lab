package org.psjobergprivat.elasticlab.testdata;

import java.util.List;

record ContainerDef(String name, List<String> fields, List<ContainerDef> children) {}
