# Functional requirements

The application is about exploring Elasticsearch. From the web GUI the user can add generated testdata to Elastic, as
well as build and execute search queries.

The application has always a top row with the same magnifying glass as the favicon in the top left corner followed by the large text Elastic Lab

The application consists of different areas, represented as separate tabs in the UI:

## Search

### Scenarios - Left, top part of page (first tab)

Pre-built search scenarios that demonstrate specific Elasticsearch features. Each scenario shows as a compact card with a title, an info button (ⓘ) that reveals a full description on hover, a pre-filled placeholder showing example inputs, and a Run button.

#### Phone — smart match (libphonenumber)

Phones indexed in any of the phone fields (`phone`, `mobile`, `work_phone`, `fax`) are parsed by libphonenumber into three normalised forms stored in dedicated catchall fields:

* `phone_all_canonical` — E.164 digit string (country code + subscriber), contributed by every phone
* `phone_all_subscriber` — subscriber digits only (no CC, no trunk), contributed by every phone
* `phone_all_subscriber_national` — subscriber digits, contributed **only** by phones written in national format

At query time the input is parsed the same way. A **national input** (no `+` or `00` prefix) matches any document that shares the subscriber digits regardless of country or notation. An **international input** matches documents in the same country (via canonical) or any document whose phone was stored in national notation (via subscriber_national); it does not cross country-code boundaries between two internationally-written numbers.

#### Email — smart match (case + tag)

Fields `email` and `secondary_email` copy their raw value to `email_all` at index time. The `email_normalizer` lowercases the address and strips plus-tags from the local part (`alice+news@example.com → alice@example.com`). The same normalisation runs on the query value at search time, so case differences and sub-addressing tags never affect whether a document matches.

#### Phone — any format (digit-strip catchall)

All phone fields copy their raw value to `phone_all`. A char filter strips everything except digits before storing, and the query is normalised the same way. Finds any number regardless of spacing or punctuation. Unlike the smart-match scenario, this does not understand country codes or trunk prefixes.

#### Phone — prefix search (edge ngram)

`phone_all.ngram` indexes every digit prefix of each stored number using edge-ngram tokenisation. Type a partial digit sequence to find all numbers that start with that prefix.

#### Multilingual text

Text fields (`body`, `summary`, `review`, `message`, etc.) are populated from Wikipedia extracts in eight languages (EN, FR, DE, ES, RU, AR, HE, ZH). Try a word in any script.

### Query Builder - Left, top part of page (second tab)

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
