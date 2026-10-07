[#if content.quote?has_content]
<blockquote>
  <p>${content.quote}</p>
  [#if content.author?has_content]<footer>— ${content.author}</footer>[/#if]
</blockquote>
[/#if]
