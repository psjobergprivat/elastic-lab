# Functional requirements

The application is about exploring Elasticsearch. From the web GUI the user can add generated testdata to Elastic, as
well as build and execute search queries.

The application consists of different areas, represented as separate tabs in the UI:

## Search

### Query Builder - Left, top part of page

Based on the current Elastic mapping arbitrary queries can be built:

* Supports nested properties
* Each property has its own input field, customized for its type. Supports wild card and other, depending on the type.
* Each property type has its own input field, meaning that the query will target all fields of that type
* An input field for searching in all properties
* A free text input field that supports elastic syntax such as targeting specific properties, wild cards and other
* Boolean operators AND, OR, NOT as well as grouping (parenthesis) can be managed graphically, and all combinations of
  all input fields above are allowed
* A clearly marked Search button to send the query

### Query Viewer - Right, top part of page

* Dynamically displays the current query that can be sent to the backend

### Query Results - Bottom part of page

#### Result List

* A clickable list of all matching documents

#### Result item

* Shown instead of the result list when an item is clicked
* Shows the full document
* Has a Back function to show the result list again

## Manage Data

* Supports nested properties

## View Metadata

Shows metadata for the Elastic server as well as one index, see below

### Elastic server metadata

* Elasticsearch server version
* Cluster name

### Elastic index metadata

Drop-down to select index. If there is only one index, still show the drop-down but also show the index data directly.

* Index name
* Number of documents in index
* Elastic index version
* Index current mappings
