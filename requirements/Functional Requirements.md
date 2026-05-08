# Functional requirements

The application is about exploring Elasticsearch. From the web GUI the user can add generated testdata to Elastic, as
well as build and execute search queries.

The application has always a top row with the same magnifying glass as the favicon in the top left corner followed by the large text Elastic Lab

The application consists of different areas, represented as separate tabs in the UI:

## Search

### Query Builder - Left, top part of page

Based on the current Elastic mapping arbitrary queries can be built:

* Supports nested fields
* Each document field has its own input field, customized for its type. Supports wild card and other, depending on the
  type.
* Each document field type has its own input field, meaning that the query will target all fields of that type
* An input field for searching in all document fields
* A free text input field that supports elastic syntax such as targeting specific fields, wild cards and other
* Boolean operators AND, OR, NOT as well as grouping (parenthesis) can be managed graphically, and all combinations of
  all input fields above are allowed
* A clearly marked Search button to send the query

### Frontend Query Viewer - Right, top part of page

* Dynamically displays the current query that can be sent to the backend

### Query Results - Left, bottom part of page

#### Result List

* A clickable list of matching documents. The full result can be paged through.

#### Result item

* Shown instead of the result list when an item is clicked
* Shows the full document
* Has a Back function to show the result list again

### Elastic Query Viewer - Right, bottom part of page

* When backend sends a query to Elastic, it keeps it in memory and passes a serialized version of it back with the
  search result to be displayed here 

## Manage Test Data

* The intent of the test data is to be a good source for doing many types of searches
* The documents should not be identical in structure and fields, but quite similar
* The field names and values should look fairly authentic
* Test data can be generated and inserted into Elastic based on a set of user entered parameters:
    * Number of documents (1-10_000_000)
    * Number of fields per document, random number between min and max (1-50)
    * Depth as in json nesting levels, min to max (1-5). At depth 1 all fields are at the root. Deeper depths activate
      domain containers (person, organization, product, server, event) and their sub-containers, keeping the total
      unique Elasticsearch field paths bounded regardless of document count.
* All data in the current index can be paged through, displayed as a list of documents on the right side
* When clicking a document, the same functionality as Search/Query Results/Result item is available 
* Without inserting anything in Elastic an example document based on current parameters can be generated and displayed
* Some sort of basic progress indicator should be displayed when working
* A "Delete all documents" function is available (somehow indicated with red color)

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
