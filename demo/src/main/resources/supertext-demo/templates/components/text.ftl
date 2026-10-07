<section>
  [#if content.headline?has_content]<h2>${content.headline}</h2>[/#if]
  [#if content.text?has_content]${cmsfn.decode(content).text}[/#if]
</section>
